package com.ethercamp.contrdata.contract;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.*;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.fasterxml.jackson.annotation.JsonInclude.Include;
import static java.lang.String.format;
import static java.util.Objects.isNull;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.math.NumberUtils.toInt;

@Getter
public class Ast {

    private static class Patterns {
        public static final Pattern LEVEL_DETECTOR = Pattern.compile("(\\s*)(.+)");

        public static final Pattern CONTRACT_DEFINITION = Pattern.compile("ContractDefinition \"(.+)\"");
        public static final Pattern STRUCTURE_DEFINITION = Pattern.compile("StructDefinition \"(.+)\"");
        public static final Pattern VARIABLE_DECLARATION = Pattern.compile("VariableDeclaration \"(.+)\"");
        public static final Pattern ENUM_DEFINITION = Pattern.compile("EnumDefinition \"(.+)\"");
        public static final Pattern ENUM_VALUE = Pattern.compile("EnumValue \"(.+)\"");


        public static final Pattern ELEMENTARY_TYPE_NAME = Pattern.compile("ElementaryTypeName (.+)");
        public static final Pattern USER_DEFINED_TYPE_NAME = Pattern.compile("UserDefinedTypeName \"(.+)\"");
        public static final Pattern CONSTANT_DETECTOR = Pattern.compile("Source:.+constant.+");
        public static final Pattern INHERITANCE_SPECIFIER = Pattern.compile("InheritanceSpecifier");
        public static final Pattern LITERAL_DETECTOR = Pattern.compile("Literal\\, token: \\[no token\\] value: (.+)");
    }

    private Root root = new Root();


    public Contract getContractAllDataMembers(String name) {
        List<Contract> hierarchy = root.getContractHierarchy(name);
        List<Variable> variables = hierarchy.stream()
                .flatMap(parent -> parent.getVariables().stream())
                .filter(var -> !var.isConstant())
                .collect(toList());
        List<Structure> structures = hierarchy.stream()
                .flatMap(parent -> parent.getStructures().stream())
                .collect(toList());
        List<Enum> enums = hierarchy.stream()
                .flatMap(parent -> parent.getEnums().stream())
                .collect(toList());

        Contract contract = new Contract(root, name);
        contract.getVariables().addAll(variables);
        contract.getStructures().addAll(structures);
        contract.getEnums().addAll(enums);

        return contract;
    }

    private static Ast parse(Scanner scanner) {
        Ast result = new Ast();
        try (Scanner lines = scanner) {
            while (lines.hasNextLine()) {
                Line line = new Line(lines.nextLine());
                result.root.apply(line);
            }
        } finally {
            result.root.resolveDeferredTypeDefinitions();
        }

        return result;
    }

    public static Ast parse(InputStream inputStream) {
        return parse(new Scanner(inputStream));
    }

    public static Ast parse(String rawAst) {
        return parse(new Scanner(rawAst));
    }

    @Getter
    @ToString
    private static class Line {

        private static final int LEVEL_INTENT_LENGTH = 2;

        private String content = EMPTY;
        private int nestingLevel;

        public Line(String content) {
            Matcher matcher = Patterns.LEVEL_DETECTOR.matcher(content);
            if (matcher.matches()) {
                this.nestingLevel = matcher.group(1).length() / LEVEL_INTENT_LENGTH;
                this.content = matcher.group(2);
            }
        }

        public Matcher matcher(Pattern pattern) {
            return pattern.matcher(content);
        }

        @Override
        public boolean equals(Object obj) {
            return content.equals(obj);
        }
    }

    @Data
    @NoArgsConstructor
    @JsonInclude(Include.NON_NULL)
    public static abstract class Entry {

        @JsonIgnore
        private int nestingLevel;
        @JsonIgnore
        private Entry parent;
        protected String name;

        Entry(Entry parent, String name) {
            this.name = name;
            this.nestingLevel = (parent == null) ? 0 : parent.getNestingLevel() + 1;
            this.parent = parent;
        }

        public void validate() {

        }

        public boolean applicable(Line line) {
            return nestingLevel < line.getNestingLevel();
        }

        public abstract void apply(Line line);

        protected Root getRoot() {
            Entry root = parent == null ? this : parent.getRoot();
            return (Root) root;
        }

        public String toJson() {
            try {
                return new ObjectMapper().writeValueAsString(this);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @NoArgsConstructor
    public static class Entries<T extends Entry> extends ArrayList<T> {

        private Pattern pattern;
        private Function<Matcher, T> function;
        private T current;

        public Entries(Pattern pattern, Function<Matcher, T> function) {
            this.pattern = pattern;
            this.function = function;
        }

        public void apply(Line line) {
            T detected = null;

            Matcher matcher = line.matcher(pattern);
            if (matcher.matches()) {
                T entry = function.apply(matcher);
                if (entry.getNestingLevel() == line.getNestingLevel()) {
                    detected = entry;
                }
            }

            if (detected != null) {
                add(detected);
                current = detected;
            } else if (current != null) {
                if (current.applicable(line)) {
                    current.apply(line);
                } else {
                    current.validate();
                    current = null;
                }
            }
        }
    }

    private static class Root extends Entry {

        private Entries<Contract> contracts = Contract.entries(this);
        private Map<String, Set<String>> userDefinedTypes = new HashMap<>();
        private List<Type.UserDefined> deferredTypeDefinitions = new ArrayList<>();

        public Root() {
            setNestingLevel(-1);
        }

        @Override
        public void apply(Line line) {
            contracts.apply(line);
        }

        private Contract findContract(String name) {
            return getOptionalContract(name).get();
        }

        private Optional<Contract> getOptionalContract(String name) {
            return contracts.stream()
                    .filter(c -> StringUtils.equals(c.getName(), name))
                    .findFirst();
        }

        public List<Contract> getContractHierarchy(String name) {
            return findContract(name).hierarchy().stream()
                    .map(contractName -> findContract(contractName))
                    .collect(toList());
        }

        private Set<String> getUserDefinedTypesNames(String type) {
            Set<String> names = userDefinedTypes.get(type);
            if (names == null) {
                names = new HashSet<>();
                userDefinedTypes.put(type, names);
            }

            return names;
        }

        public void onUserDefinedDetected(String type, String name) {
            getUserDefinedTypesNames(type).add(name);
        }

        public boolean isUserDefined(String type, String name) {
            return getUserDefinedTypesNames(type).contains(name);
        }

        public void addDeferredTypeDefinition(Type.UserDefined undefinedType) {
            deferredTypeDefinitions.add(undefinedType);
        }

        private void resolveTypeDefinitions(Type.UserDefined type) {
            for (String typeName : userDefinedTypes.keySet()) {
                if (isUserDefined(typeName, type.getType())) {
                    type.setName(typeName);
                    return;
                }
            }
        }

        public void resolveDeferredTypeDefinitions() {
            deferredTypeDefinitions.stream().forEach(this::resolveTypeDefinitions);
        }
    }

    @Data
    @NoArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    public static class Inheritance extends Entry {

        Inheritance(Entry parent, String name) {
            super(parent, name);
        }

        @JsonCreator
        public static Inheritance fromJson(String name) {
            return new Inheritance(null, name);
        }

        @Override
        public void apply(Line line) {
            if (applicable(line) && isEmpty(name)) {
                Matcher matcher = line.matcher(Patterns.USER_DEFINED_TYPE_NAME);
                if (matcher.matches()) {
                    name = matcher.group(1);
                }
            }
        }

        @Override
        @JsonValue
        public String getName() {
            return super.getName();
        }

        public static Ast.Entries<Inheritance> entries(Contract contract) {
            return new Entries<>(Patterns.INHERITANCE_SPECIFIER, matcher -> new Inheritance(contract, null));
        }
    }

    @Data
    @NoArgsConstructor
    @EqualsAndHashCode
    public static class Contract extends Ast.Entry {

        private Entries<Inheritance> inheritances = Inheritance.entries(this);
        private Entries<Enum> enums = Enum.entries(this);
        private Entries<Structure> structures = Structure.entries(this);
        private Entries<Variable> variables = Variable.entries(this);

        Contract(Root root, String name) {
            super(root, name);
        }

        public List<String> hierarchy() {
            List<String> result = new ArrayList<>();
            for (int i = inheritances.size() - 1; i >= 0; i--) {
                Inheritance inheritance = inheritances.get(i);
                Contract parent = getRoot().findContract(inheritance.getName());
                List<String> hierarchy = parent.hierarchy().stream()
                        .filter(parentHierarchy -> !result.contains(parentHierarchy))
                        .collect(toList());

                result.addAll(0, hierarchy);
            }
            result.add(this.getName());

            return result;
        }

        @Override
        public void apply(Ast.Line line) {
            inheritances.apply(line);
            enums.apply(line);
            structures.apply(line);
            variables.apply(line);
        }

        public static Ast.Entries<Contract> entries(Root root) {
            return new Entries<>(Patterns.CONTRACT_DEFINITION, matcher -> {
                String name = matcher.group(1);
                root.onUserDefinedDetected(Type.Names.CONTRACT, name);
                return new Contract(root, name);
            });
        }

        public static Contract fromJson(String json) {
            try {
                return new ObjectMapper().readValue(json, Contract.class);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Data
    @NoArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    public static class Structure extends Ast.Entry {

        private final Ast.Entries<Variable> variables = Variable.entries(this);

        Structure(Contract contract, String name) {
            super(contract, name);
        }

        @Override
        public void apply(Ast.Line line) {
            variables.apply(line);
        }

        public static Ast.Entries<Structure> entries(Contract contract) {
            return new Entries<>(Patterns.STRUCTURE_DEFINITION, matcher -> {
                String name = matcher.group(1);
                contract.getRoot().onUserDefinedDetected(Type.Names.STRUCT, name);
                return new Structure(contract, name);
            });
        }
    }

    @Data
    @NoArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    public static class Enum extends Ast.Entry {

        private Entries<EnumValue> values = EnumValue.entries(this);

        public Enum(Contract contract, String name) {
            super(contract, name);
        }

        @Override
        public void apply(Line line) {
            values.apply(line);
        }

        public static Ast.Entries<Enum> entries(Contract contract) {
            return new Entries<>(Patterns.ENUM_DEFINITION, matcher -> {
                String name = matcher.group(1);
                contract.getRoot().onUserDefinedDetected(Type.Names.ENUM, name);
                return new Enum(contract, name);
            });
        }
    }

    @Data
    @NoArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    public static class EnumValue extends Ast.Entry {

        @JsonCreator
        public EnumValue(String name) {
            super(null, name);
        }

        public EnumValue(Enum parent, String name) {
            super(parent, name);
        }

        @Override
        public void apply(Line line) {

        }

        @JsonValue
        @Override
        public String getName() {
            return super.getName();
        }

        public static Ast.Entries<EnumValue> entries(Enum anEnum) {
            return new Entries<>(Patterns.ENUM_VALUE, matcher -> new EnumValue(anEnum, matcher.group(1)));
        }
    }

    @Data
    @NoArgsConstructor
    @EqualsAndHashCode(callSuper = true)
    public static class Variable extends Ast.Entry {

        private boolean constant;
        private Type type;

        public Variable(Ast.Entry parent, String name) {
            super(parent, name);
        }

        @Override
        public void apply(Ast.Line line) {
            if (type == null) {
                type = Type.detectAndCreate(this, line);
                if (type == null && !constant) {
                    constant = line.matcher(Patterns.CONSTANT_DETECTOR).matches();
                }
            } else {
                type.apply(line);
            }
        }

        public static Ast.Entries<Variable> entries(Ast.Entry parent) {
            return new Entries<>(Patterns.VARIABLE_DECLARATION, matcher -> new Variable(parent, matcher.group(1)));
        }
    }

    @NoArgsConstructor
    public static abstract class Type extends Ast.Entry {

        protected static class Names {
            public static final String MAPPING = "mapping";
            public static final String ARRAY = "array";
            public static final String STRUCT = "struct";
            public static final String ENUM = "enum";
            public static final String CONTRACT = "contract";

            public static final String UNKNOWN = "unknown";
        }

        Type(Ast.Entry parent, String name) {
            super(parent, name);
        }

        @Override
        public void apply(Ast.Line line) {

        }

        @JsonIgnore
        public boolean is(Predicate<String> predicate) {
            return predicate.test(getName());
        }

        @JsonIgnore
        public boolean is(String typeName) {
            return is(name -> StringUtils.equals(typeName, name));
        }

        @JsonIgnore
        public boolean isElementary() {
            return false;
        }

        @JsonIgnore
        public boolean isContainer() {
            return false;
        }

        @JsonIgnore
        public boolean isUserDefined() {
            return false;
        }

        @JsonIgnore
        public boolean isMapping() {
            return is(Names.MAPPING);
        }

        @JsonIgnore
        public boolean isArray() {
            return is(Names.ARRAY);
        }

        @JsonIgnore
        public boolean isStruct() {
            return is(Names.STRUCT);
        }

        @JsonIgnore
        public boolean isStructArray() {
            return this.isArray() && this.as(Array.class).getElementType().isStruct();
        }

        @JsonIgnore
        public boolean isStaticArray() {
            return isArray() && this.asArray().isStatic();
        }

        @JsonIgnore
        public boolean isEnum() {
            return is(Names.ENUM);
        }

        @JsonIgnore
        public boolean isContract() {
            return is(Names.CONTRACT);
        }

        protected <T> T as(Class<T> castClass) {
            return (T) this;
        }

        public Array asArray() {
            return as(Array.class);
        }

        public Mapping asMapping() {
            return as(Mapping.class);
        }

        public Struct asStruct() {
            return as(Struct.class);
        }

        public Enum asEnum() {
            return as(Enum.class);
        }

        public Elementary asElementary() {
            return as(Elementary.class);
        }

        public String formatName() {
            return getName();
        }

        public static Type detectAndCreate(Ast.Entry parent, Ast.Line line) {
            Matcher matcher = line.matcher(Patterns.ELEMENTARY_TYPE_NAME);
            if (matcher.matches()) {
                String name = matcher.group(1);
                return new Elementary(parent, name);
            }

            matcher = line.matcher(Patterns.USER_DEFINED_TYPE_NAME);
            if (matcher.matches()) {
                String name = matcher.group(1);
                Root root = parent.getRoot();
                if (root.isUserDefined(Names.STRUCT, name)) {
                    return new Struct(parent, name);
                } else if (root.isUserDefined(Names.ENUM, name)) {
                    return new Enum(parent, name);
                } else if (root.isUserDefined(Names.CONTRACT, name)) {
                    return new Contract(parent, name);
                } else {
                    UserDefined undefined = new UserDefined(parent, Names.UNKNOWN, name);
                    root.addDeferredTypeDefinition(undefined);
                    return undefined;
                }
            }

            if (line.equals("Mapping")) {
                return new Mapping(parent);
            }

            if (line.equals("ArrayTypeName")) {
                return new Array(parent);
            }

            return null;
        }

        @JsonCreator
        public static Type fromJson(Object json) {
            return (json instanceof String)
                    ? fromJson((String) json)
                    : fromJson((Map<String, Object>) json);
        }

        @JsonCreator
        public static Type fromJson(String typeName) {
            return new Elementary(null, typeName);
        }

        @JsonCreator
        public static Type fromJson(Map<String, Object> typeProps) {
            final String name = (String) typeProps.get("name");

            if (Names.ARRAY.equals(name)) {
                Array array = new Array(null);
                array.setElementType(fromJson(typeProps.get("elementType")));
                array.setSize((Integer) typeProps.computeIfAbsent("size", s -> 0));
                return array;
            }

            if (Names.MAPPING.equals(name)) {
                Mapping mapping = new Mapping(null);
                mapping.setKeyType(fromJson(typeProps.get("keyType")));
                mapping.setValueType(fromJson(typeProps.get("valueType")));
                return mapping;
            }

            if (Names.STRUCT.equals(name)) {
                return new Struct(null, (String) typeProps.get("type"));
            }

            if (Names.ENUM.equals(name)) {
                return new Enum(null, (String) typeProps.get("type"));
            }

            if (Names.CONTRACT.equals(name)) {
                return new Contract(null, (String) typeProps.get("type"));
            }

            return null;
        }

        @NoArgsConstructor
        public static class Elementary extends Type {

            public Elementary(Ast.Entry parent, String name) {
                super(parent, name);
            }

            @Override
            @JsonValue
            public String getName() {
                return super.getName();
            }

            @Override
            public boolean isElementary() {
                return true;
            }

            @JsonIgnore
            public boolean isString() {
                return is("string");
            }

            @JsonIgnore
            public boolean isBool() {
                return is("bool");
            }

            @JsonIgnore
            public boolean isAddress() {
                return is("address");
            }

            @JsonIgnore
            public boolean isNumber() {
                return is(name1 -> StringUtils.contains(name1, "int"));
            }
        }

        @Data
        @NoArgsConstructor
        @EqualsAndHashCode(callSuper = true)
        public static abstract class Container extends Type {

            public Container(Entry parent, String name) {
                super(parent, name);
            }

            @Override
            @JsonIgnore
            public boolean isContainer() {
                return true;
            }
        }

        @Data
        @NoArgsConstructor
        @EqualsAndHashCode(callSuper = true)
        public static class Array extends Container {

            private Type elementType;
            private Integer size;

            Array(Ast.Entry parent) {
                super(parent, Names.ARRAY);
            }

            @JsonIgnore
            public boolean isStatic() {
                return size != null && size > 0;
            }

            @Override
            public void apply(Ast.Line line) {
                if (elementType == null) {
                    elementType = Type.detectAndCreate(this, line);
                } else if (elementType.applicable(line)) {
                    elementType.apply(line);
                } else if (isNull(size)) {
                    Matcher matcher = line.matcher(Patterns.LITERAL_DETECTOR);
                    if (matcher.matches()) {
                        String size = matcher.group(1);
                        this.size = toInt(size);
                    }
                }
            }

            @Override
            public String formatName() {
                return format("%s[%s]", elementType.formatName(), isStatic() ? size : EMPTY);
            }
        }

        @Data
        @NoArgsConstructor
        @EqualsAndHashCode(callSuper = true)
        public static class Mapping extends Container {

            private Type keyType;
            private Type valueType;

            Mapping(Ast.Entry parent) {
                super(parent, Names.MAPPING);
            }

            @Override
            public void apply(Ast.Line line) {
                if (keyType == null) {
                    keyType = Type.detectAndCreate(this, line);
                } else if (valueType == null && keyType.applicable(line)) {
                    keyType.apply(line);
                } else if (valueType == null) {
                    valueType = Type.detectAndCreate(this, line);
                } else if (valueType.applicable(line)) {
                    valueType.apply(line);
                }
            }

            @Override
            public String formatName() {
                return format("mapping(%s=>%s)", keyType.formatName(), valueType.formatName());
            }
        }

        @Data
        @NoArgsConstructor
        @EqualsAndHashCode(callSuper = true)
        public static class UserDefined extends Type {

            private String type;

            UserDefined(Ast.Entry parent, String name, String type) {
                super(parent, name);
                this.type = type;
            }

            @Override
            public boolean isUserDefined() {
                return true;
            }

            @Override
            public String formatName() {
                return type;
            }
        }

        @Data
        @NoArgsConstructor
        @EqualsAndHashCode(callSuper = true)
        public static class Struct extends UserDefined {

            Struct(Ast.Entry parent, String type) {
                super(parent, Names.STRUCT, type);
            }
        }

        @Data
        @NoArgsConstructor
        @EqualsAndHashCode(callSuper = true)
        public static class Enum extends UserDefined {

            Enum(Ast.Entry parent, String type) {
                super(parent, Names.ENUM, type);
            }
        }

        @Data
        @NoArgsConstructor
        @EqualsAndHashCode(callSuper = true)
        public static class Contract extends UserDefined {

            Contract(Ast.Entry parent, String type) {
                super(parent, Names.CONTRACT, type);
            }
        }
    }
}