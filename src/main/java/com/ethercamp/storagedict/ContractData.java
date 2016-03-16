package com.ethercamp.storagedict;

import com.ethercamp.storagedict.concept.Path;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.math.NumberUtils.toInt;

public class ContractData {

    private List<Member> members;
    private Map<String, List<Member>> fieldsByStructName;
    private Map<String, List<String>> valuesByEnumName;

    ContractData(Ast.Contract contract) {
        this.members = mapVariablesToMembers(contract, contract.getVariables());
        this.fieldsByStructName = contract.getStructures().stream()
                .collect(toMap(Ast.Structure::getName, structure -> mapVariablesToMembers(contract, structure.getVariables())));
        this.valuesByEnumName = contract.getEnums().stream()
                .collect(toMap(Ast.Enum::getName, anEnum -> anEnum.getValues().stream().map(Ast.EnumValue::getName).collect(toList())));
    }

    private static List<Member> mapVariablesToMembers(Ast.Contract contract, Ast.Entries<Ast.Variable> variables) {
        List<Member> result = new ArrayList<>();
        for (int i = 0, storageIndex = 0; i < variables.size(); i++, storageIndex++) {
            Ast.Variable variable = variables.get(i);
            result.add(new Member(storageIndex, variable));

            if (variable.getType().isStruct()) {
                storageIndex += getExpandedStructFieldsCount(contract, variable.getType().asStruct()) - 1;
            } else if (variable.getType().isStaticArray()) {
                Ast.Type.Array array = variable.getType().asArray();
                if (array.isStructArray()) {
                    storageIndex += getExpandedStructFieldsCount(contract, array.getElementType().asStruct()) * array.getSize() - 1;
                } else {
                    storageIndex += array.getSize() - 1;
                }
            }
        }

        return result;
    }

    private static int getExpandedStructFieldsCount(Ast.Contract contract, Ast.Type.Struct structType) {
        int result = 0;
        List<Ast.Variable> fields = contract.getStructures().stream()
                .filter(struct -> struct.getName().equals(structType.getType()))
                .findFirst()
                .get()
                .getVariables();

        for (Ast.Variable field : fields) {
            if (field.getType().isStruct()) {
                result += getExpandedStructFieldsCount(contract, field.getType().asStruct());
            } else {
                result++;
            }
        }

        return result;
    }

    public static ContractData parse(String json) {
        return new ContractData(Ast.Contract.fromJson(json));
    }

    public int getExpandedStructFieldsCount(Ast.Type.Struct structType) {
        int result = 0;
        for (Member field : getStructureFields(structType)) {
            if (field.getType().isStruct()) {
                result += getExpandedStructFieldsCount(field.getType().asStruct());
            } else {
                result++;
            }
        }
        return result;
    }

    public Member getMemberByStorageIndex(List<Member> members, int index) {
        for (Member member : members) {
            if (member.index() == index) {
                return member;
            } else if (member.getType().isStruct()) {
                int fieldsCount = getExpandedStructFieldsCount(member.getType().asStruct());
                if (index <= member.storageIndex(fieldsCount - 1)) {
                    List<Member> fields = getStructureFields(member.getType());
                    return getMemberByStorageIndex(fields, index - member.index());
                }
            }
        }

        return null;
    }

    public Member getMemberByStorageIndex(int index) {
        return getMemberByStorageIndex(getMembers(), index);
    }

    public List<Member> getMembers() {
        return members;
    }

    public List<Member> getStructureFields(String structName) {
        return fieldsByStructName.get(structName);
    }

    public List<Member> getStructureFields(Ast.Type.Struct structType) {
        return getStructureFields(structType.getType());
    }

    public List<Member> getStructureFields(Ast.Type structType) {
        return getStructureFields(structType.asStruct());
    }

    public List<Member> getStructureFields(Ast.Type.Array structArrayType) {
        return getStructureFields(structArrayType.getElementType());
    }

    public List<String> getEnumValues(Ast.Type.Enum enumType) {
        return valuesByEnumName.get(enumType.getType());
    }

    public String getEnumValueByOrdinal(Ast.Type.Enum enumType, int ordinal) {
        return getEnumValues(enumType).get(ordinal);
    }

    public static class Member {

        private String index;
        private Ast.Variable variable;

        public Member(int index, Ast.Variable variable) {
            this.index = String.valueOf(index);
            this.variable = variable;
        }

        public String getIndex() {
            return index;
        }

        public Ast.Type getType() {
            return variable.getType();
        }

        public String getName() {
            return variable.getName();
        }

        public int index() {
            return toInt(index);
        }

        public int storageIndex(int offset) {
            return offset + index();
        }
    }


    public PathElementType elementTypeByPath(Path path) {
        Iterator<String> itr = path.iterator();

        Member member = getMemberByStorageIndex(toInt(itr.next()));
        PathElementType result = new PathElementType(member);

        while (itr.hasNext()) {
            result = result.addNext(itr.next());
        }

        return result;
    }

    public class PathElementType {

        private String id;
        private Ast.Type type;
        private PathElementType prev;
        private PathElementType next;

        private PathElementType(String id, Ast.Type type, PathElementType previous) {
            this.id = id;
            this.type = type;
            this.prev = previous;
        }

        public PathElementType(Member member) {
            this(member.getIndex(), member.getType(), null);
        }

        public Ast.Type getType() {
            return type;
        }

        public PathElementType getPrev() {
            return prev;
        }

        public PathElementType getNext() {
            return next;
        }

        public PathElementType addNext(String id) {
            return this.next = new PathElementType(id, nestedType(id), this);
        }

        private Ast.Type nestedType(String id) {
            if (type.isMapping()) {
                return type.asMapping().getValueType();
            } else if (type.isArray()) {
                return type.asArray().getElementType();
            } else if (type.isStruct()) {
                List<Member> fields = getStructureFields(type.asStruct());
                ContractData.Member field = getMemberByStorageIndex(fields, toInt(id));
                return field.getType();
            } else {
                throw new UnsupportedOperationException("Elementary type hasn't nested types");
            }
        }

        private List<Object> dictionaryPath() {
            List<Object> result = isFirst() ? new ArrayList<>() : prev.dictionaryPath();
            if (!isFirst()) {
                if (prev.getType().isStructArray()) {
                    Ast.Type.Struct structType = type.asStruct();
                    int fieldsCount = getExpandedStructFieldsCount(structType);
                    result.add(asIndex() * fieldsCount);
                } else if (prev.getType().isStruct()) {
                    int lastIndex = result.size() - 1;
                    Object last = result.remove(lastIndex);
                    result.add(asIndex() + (last instanceof String ? toInt((String) last) : (int) last));
                } else {
                    result.add(id);
                }
            } else {
                result.add(id);
            }

            return result;
        }

        public Path path() {
            return isFirst() ? new Path(id) : prev.path().extend(id);
        }

        public List<StorageDictionary.PathElement> getChildren(StorageDictionary dictionary) {
            String[] pathParts = dictionaryPath().stream()
                    .map(Object::toString)
                    .toArray(String[]::new);

            Path path = new Path(pathParts);

            if (type.isStruct()) {
                if (!isFirst() && prev.getType().isMapping()) {
                    return dictionary.getByPath(path.parts()).getChildrenStream().collect(toList());
                } else {
                    int offset = toInt(path.lastPart());
                    Map<String, String> indexTranslateMap = getStructureFields(type).stream()
                            .collect(toMap(field -> String.valueOf(field.storageIndex(offset)), Member::getIndex));

                    List<StorageDictionary.PathElement> children = dictionary.getByPath(path.initial().parts()).getChildrenStream().collect(toList());
                    if (isNotEmpty(children)) {
                        children = children.stream()
                                .filter(child -> indexTranslateMap.keySet().contains(child.key))
                                .map(child -> {
                                    StorageDictionary.PathElement clone = child.clone();
                                    clone.key = indexTranslateMap.get(child.key);
                                    return clone;
                                })
                                .collect(toList());
                    }

                    return children;
                }

            } else if (type.isStructArray()) {

                Ast.Type.Struct structType = type.asArray().getElementType().asStruct();
                int fieldsCount = getExpandedStructFieldsCount(structType);

                List<StorageDictionary.PathElement> children = dictionary.getByPath(path.parts()).getChildrenStream().collect(toList());
                List<StorageDictionary.PathElement> filtered = new ArrayList<>();
                for (int i = 0; i < children.size(); i++) {
                    if (i % fieldsCount == 0) {
                        StorageDictionary.PathElement pe = children.get(i).clone();
                        pe.key = String.valueOf(i / fieldsCount);
                        filtered.add(pe);
                    }
                }

                return filtered;

            } else if (type.isStaticArray()) {
                int firstIndex = toInt(path.lastPart());
                int lastIndex = type.asArray().getSize();

                List<StorageDictionary.PathElement> children = new ArrayList<>();
                for (StorageDictionary.PathElement child : dictionary.getByPath(path.initial().parts()).getChildren()) {
                    int childIndex = toInt(child.key);
                    if (childIndex >= firstIndex && childIndex < lastIndex) {
                        child = child.clone();
                        child.key = String.valueOf(childIndex - firstIndex);
                        children.add(child);
                    }
                }

                return children;
            } else {
                return dictionary.getByPath(path.parts()).getChildrenStream().collect(toList());
            }
        }

        public boolean isFirst() {
            return prev == null;
        }

        public boolean isLast() {
            return next == null;
        }

        public int asIndex() {
            return toInt(id);
        }
    }
}
