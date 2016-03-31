package com.ethercamp.contrdata.contract;

import com.ethercamp.contrdata.storage.Path;
import com.ethercamp.contrdata.storage.dictionary.StorageDictionary;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.DataWord;
import org.spongycastle.util.encoders.Hex;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.ArrayUtils.*;
import static org.apache.commons.lang3.math.NumberUtils.toInt;
import static org.ethereum.util.ByteUtil.toHexString;

public class ContractData {

    private static final Pattern DATA_WORD_PATTERN = Pattern.compile("[0-9a-fA-f]{64}");

    private final Ast.Contract contract;
    private final Map<String, Members> structFields;
    private final Members contractMembers;
    private final Map<String, List<String>> enumValues;

    public ContractData(Ast.Contract contract, StorageDictionary dictionary) {
        this.dictionary = dictionary;
        this.contract = contract;
        this.contractMembers = Members.ofContract(this);
        this.structFields = contract.getStructures().stream().collect(toMap(Ast.Structure::getName, struct -> Members.ofStructure(this, struct)));
        this.enumValues = contract.getEnums().stream()
                .collect(toMap(Ast.Enum::getName, anEnum -> anEnum.getValues().stream().map(Ast.EnumValue::getName).collect(toList())));

    }

    public Members getStructFields(Ast.Type.Struct struct) {
        return getStructFields(struct.getType());
    }

    public Members getStructFields(String name) {
        return structFields.get(name);
    }

    public Members getMembers() {
        return contractMembers;
    }

    public Ast.Contract getContract() {
        return contract;
    }

    public List<String> getEnumValues(Ast.Type.Enum enumType) {
        return enumValues.get(enumType.getType());
    }

    public String getEnumValueByOrdinal(Ast.Type.Enum enumType, int ordinal) {
        return getEnumValues(enumType).get(ordinal);
    }

    public static ContractData parse(String asJson, StorageDictionary dictionary) {
        Ast.Contract contract = Ast.Contract.fromJson(asJson);
        return new ContractData(contract, dictionary);
    }

    private StorageDictionary dictionary;
    private Map<Element, StorageDictionary.PathElement> elementTranslateMap = new HashMap<>();

    private StorageDictionary.PathElement translateToPathElement(Element element) {
        return elementTranslateMap.computeIfAbsent(element, el -> dictionary.getByPath(el.dictionaryPath().parts()));
    }

    public Element elementByPath(Object... pathParts) {
        RootElementImpl rootElement = new RootElementImpl();

        Path path = Path.of(pathParts);
        if (path.isEmpty()) {
            return rootElement;
        }

        Iterator<String> itr = path.iterator();
        Member member = getMembers().get(toInt(itr.next()));

        ElementImpl result = new ElementImpl(member, rootElement);
        while (itr.hasNext()) {
            result = new ElementImpl(itr.next(), result);
        }

        return result;
    }

    public abstract class Element {

        public Path path() {
            return Path.empty();
        }

        protected Path dictionaryPath() {
            return Path.empty();
        }

        public StorageDictionary.PathElement toDictionaryPathElement() {
            return translateToPathElement(this);
        }

        public abstract int getChildrenCount();

        public abstract List<Element> getChildren(int page, int size);

        public List<Element> getAllChildren() {
            return getChildren(0, getChildrenCount());
        }

        public Ast.Type getType() {
            throw new UnsupportedOperationException();
        }

        public Element getParent() {
            return null;
        }

        public String getKey() {
            throw new UnsupportedOperationException();
        }

        public String getValue(Function<DataWord, DataWord> valueExtractor) {
            throw new UnsupportedOperationException();
        }

        public DataWord getStorageValue(Function<DataWord, DataWord> valueExtractor) {
            throw new UnsupportedOperationException();
        }

        public boolean isRoot() {
            return false;
        }

        Member getMember() {
            throw new UnsupportedOperationException();
        }
    }

    @EqualsAndHashCode
    public class RootElementImpl extends Element {

        @Override
        public boolean isRoot() {
            return true;
        }

        @Override
        public int getChildrenCount() {
            return getMembers().size();
        }

        @Override
        public List<Element> getChildren(int page, int size) {
            return getMembers().page(page, size).stream()
                    .map(member -> new ElementImpl(member, this))
                    .collect(toList());
        }
    }

    @Getter
    @EqualsAndHashCode(of = {"id", "name", "parent"})
    public class ElementImpl extends Element {

        private final String id;
        private final Ast.Type type;
        private final Element parent;

        private Member member;

        private ElementImpl(String id, Ast.Type type, Element previous) {
            this.id = id;
            this.type = type;
            this.parent = previous;
        }

        ElementImpl(Member member, Element previous) {
            this(String.valueOf(member.getPosition()), member.getType(), previous);
            this.member = member;
        }

        ElementImpl(String id, ElementImpl previous) {
            this(id, previous.nestedType(id), previous);
            if (previous.getType().isStruct()) {
                this.member = getStructFields(previous.getType().asStruct()).findByPosition(toInt(id));
            }
        }

        private Ast.Type nestedType(String id) {
            if (type.isMapping()) {
                return type.asMapping().getValueType();
            } else if (type.isArray()) {
                return type.asArray().getElementType();
            } else if (type.isStruct()) {
                return getStructFields(type.asStruct()).findByPosition(toInt(id)).getType();
            } else {
                throw new UnsupportedOperationException("Elementary type hasn't nested types");
            }
        }

        @Override
        public Path path() {
            return getParent().path().extend(id);
        }

        @Override
        public Path dictionaryPath() {
            Path path = getParent().dictionaryPath();

            if (getParent().isRoot()) {
                return path.extend(member.getStorageIndex());
            }

            Ast.Type parentType = getParent().getType();
            if (parentType.isStaticArray()) {
                int reservedSlotsCount = this.type.isStruct() ? getStructFields(this.type.asStruct()).reservedSlotsCount() : 1;
                int startIndex = toInt(path.remove(path.size() - 1)) + toInt(id) * reservedSlotsCount;
                return path.extend(startIndex);
            }

            if (parentType.isStructArray()) {
                int fieldsCount = getStructFields(this.type.asStruct()).reservedSlotsCount();
                return path.extend(toInt(id) * fieldsCount);
            }

            Element grandParent = getParent().getParent();
            if (parentType.isStruct() && !(grandParent.isRoot() || grandParent.getType().isMapping())) {
                Object structOffset = path.remove(path.size() - 1);
                int startIndex = member.getStorageIndex() + (structOffset instanceof String ? toInt((String) structOffset) : (int) structOffset);
                return path.extend(startIndex);
            }

            return path.extend(id);
        }

        @Override
        public int getChildrenCount() {
            int result = 0;

            if (type.isContainer()) {
                StorageDictionary.PathElement element = toDictionaryPathElement();
                if (element != null) {
                    result = element.getChildrenCount();
                }

                if (type.isArray()) {
                    int slotsPerElement = 1;
                    if (type.isStructArray()) {
                        Ast.Type.Struct structType = type.asArray().getElementType().asStruct();
                        slotsPerElement = getStructFields(structType).reservedSlotsCount();
                    }

                    if (type.isStaticArray()) {
                        int offset = member.getStorageIndex();
                        int size = type.asArray().getSize() * slotsPerElement;

                        result = 0;
                        for (StorageDictionary.PathElement child : getParent().toDictionaryPathElement().getChildren()) {
                            int current = toInt(child.key);
                            if (current >= offset + size) {
                                break;
                            }
                            if (current >= offset) {
                                result++;
                            }
                        }
                    }

                    result /= slotsPerElement;
                }
            } else if (type.isStruct()) {
                result = getStructFields(type.asStruct()).size();
            }

            return result;
        }

        @Override
        public List<Element> getChildren(int page, int size) {
            List<Element> result = emptyList();

            int offset = page * size;
            int fromIndex = max(0, offset);
            int toIndex = min(getChildrenCount(), offset + size);

            if (fromIndex < toIndex) {
                if (type.isStruct()) {
                    result = getStructFields(type.asStruct()).page(page, size).stream()
                            .map(field -> new ElementImpl(field, this))
                            .collect(toList());
                } else if (type.isArray()) {

                    IntStream indexStream;

                    if (type.isStaticArray()) {

                        int slotsPerElement = 1;
                        if (type.isStructArray()) {
                            Ast.Type.Struct structType = type.asArray().getElementType().asStruct();
                            slotsPerElement = getStructFields(structType).reservedSlotsCount();
                        }

                        int startIndex = member.getStorageIndex();
                        int reservedSlotsCount = type.asArray().getSize() * slotsPerElement;

                        List<Integer> indexes = new ArrayList<>();
                        for (StorageDictionary.PathElement child : getParent().toDictionaryPathElement().getChildren()) {
                            int index = toInt(child.key) - startIndex;
                            if (index >= reservedSlotsCount) break;

                            if (index >= 0 && (index % slotsPerElement == 0)) {
                                indexes.add(index / slotsPerElement);
                            }
                        }

                        indexStream = indexes.subList(fromIndex, toIndex).stream().mapToInt(Integer::intValue);
                    } else {
                        indexStream = IntStream.range(fromIndex, toIndex);
                    }

                    result = indexStream
                            .mapToObj(i -> new ElementImpl(String.valueOf(i), type.asArray().getElementType(), this))
                            .collect(toList());

                } else if (type.isMapping()) {
                    result = toDictionaryPathElement().getChildren(page * size, size).stream()
                            .map(pe -> new ElementImpl(pe.key, this))
                            .collect(toList());
                }
            }

            return result;
        }

        @Override
        public String getKey() {
            String result = id;

            if (member != null) {
                result = member.getName();
            } else if (getParent().getType().isMapping() && isDataWord(id)) {
                Ast.Type type = getParent().getType().asMapping().getKeyType();
                result = guessRawValueType(new DataWord(id), type, () -> id.getBytes()).toString();
            }

            return result;
        }

        @Override
        public DataWord getStorageValue(Function<DataWord, DataWord> valueExtractor) {
            if (type.isContainer()) {
                throw new UnsupportedOperationException("Cannot extract storage value for container element.");
            }

            DataWord result = null;
            StorageDictionary.PathElement pe = toDictionaryPathElement();
            if (pe != null) {
                result = valueExtractor.apply(new DataWord(pe.storageKey));
                if (member != null) {
                    result = member.extractValue(result);
                }
            }

            return result;
        }

        @Override
        public String getValue(Function<DataWord, DataWord> valueExtractor) {
            DataWord rawValue = getStorageValue(valueExtractor);
            Object typed = guessRawValueType(rawValue, type, () -> {

                StorageDictionary.PathElement pathElement = toDictionaryPathElement();
                if (pathElement == null) {
                    return EMPTY_BYTE_ARRAY;
                }

                if (pathElement.hasChildren()) {
                    byte[][] bytes = pathElement.getChildrenStream()
                            .map(child -> valueExtractor.apply(new DataWord(child.storageKey)))
                            .filter(Objects::nonNull)
                            .map(DataWord::getData)
                            .toArray(byte[][]::new);

                    if (isNotEmpty(bytes)) {
                        return ByteUtil.merge(bytes);
                    }
                }

                DataWord value = valueExtractor.apply(new DataWord(pathElement.storageKey));
                return (value == null) ? EMPTY_BYTE_ARRAY : value.getData();
            });

            return Objects.toString(typed, null);
        }

        private Object guessRawValueType(DataWord rawValue, Ast.Type type, Supplier<byte[]> bytesExtractor) {
            Object result = rawValue;

            if (type.isEnum()) {
                result = getEnumValueByOrdinal(type.asEnum(), (rawValue == null) ? 0 : rawValue.intValue());
            } else if (type.isContract() && rawValue != null) {
                result = toHexString(rawValue.getLast20Bytes());
            } else if (type.isElementary()) {
                Ast.Type.Elementary elementary = type.asElementary();
                if (elementary.isString()) {
                    byte[] bytes = bytesExtractor.get();
                    bytes = subarray(bytes, 0, indexOf(bytes, (byte) 0));
                    if (getLength(bytes) == 32) {
                        bytes = subarray(bytes, 0, 31);
                    }

                    result = new String(bytes);
                } else if (elementary.is("bytes")) {
                    result = Hex.toHexString(bytesExtractor.get());
                } else if (elementary.isBool()) {
                    result = !(rawValue == null || rawValue.isZero());
                } else if (elementary.isAddress() && rawValue != null) {
                    result = toHexString(rawValue.getLast20Bytes());
                } else if (elementary.isNumber()) {
                    result = (rawValue == null) ? 0 : rawValue.bigIntValue();
                }
            }

            return result;
        }
    }

    private static boolean isDataWord(String input) {
        return DATA_WORD_PATTERN.matcher(input).matches();
    }

}
