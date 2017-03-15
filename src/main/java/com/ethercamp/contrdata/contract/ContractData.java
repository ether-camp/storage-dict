package com.ethercamp.contrdata.contract;

import com.ethercamp.contrdata.storage.Path;
import com.ethercamp.contrdata.storage.dictionary.StorageDictionary;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.apache.commons.lang3.ArrayUtils;
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
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.*;
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

    @Getter
    private StorageDictionary dictionary;
    private Map<Element, StorageDictionary.PathElement> elementTranslateMap = new HashMap<>();

    private StorageDictionary.PathElement translateToPathElement(Element element) {
        return elementTranslateMap.computeIfAbsent(element, el -> {
            String[] parts = el.dictionaryPath().parts();
            StorageDictionary.PathElement result = dictionary.getByPath(parts);
            return result;
        });
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

        public abstract int getChildrenCount(boolean ignoreEmpty);

        public int getChildrenCount() {
            return getChildrenCount(false);
        }

        public abstract List<Element> getChildren(int page, int size, boolean ignoreEmpty);

        public List<Element> getChildren(int page, int size) {
            return getChildren(page, size, false);
        }

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
        public int getChildrenCount(boolean ignoreEmpty) {
            return (ignoreEmpty ? getExistedMembers() : getMembers()).size();
        }

        @Override
        public List<Element> getChildren(int page, int size, boolean ignoreEmpty) {
            Members members = ignoreEmpty ? getExistedMembers() : getMembers();
            return members.page(page, size).stream()
                    .map(member -> new ElementImpl(member, this))
                    .collect(toList());
        }

        private Members getExistedMembers() {
            Set<Integer> storageIndexes = toDictionaryPathElement().getChildrenStream().map(pe -> toInt(pe.key)).collect(toSet());
            return getMembers().filter(m -> storageIndexes.contains(m.getStorageIndex()));
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
            }
            if (type.isArray()) {
                return type.asArray().getElementType();
            }
            if (type.isStruct()) {
                return getStructFields(type.asStruct()).findByPosition(toInt(id)).getType();
            }

            throw new UnsupportedOperationException("Elementary type hasn't nested types");
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
                float reservedSlotsCount = this.type.isStruct() ? getStructFields(this.type.asStruct()).reservedSlotsCount() : (float) Member.size(type) / Member.BYTES_IN_SLOT;
                int startIndex = toInt(path.removeLast()) + (int) (toInt(id) * reservedSlotsCount);
                return path.extend(startIndex);
            }

            if (parentType.isStructArray()) {
                int fieldsCount = getStructFields(this.type.asStruct()).reservedSlotsCount();
                return path.extend(toInt(id) * fieldsCount);
            }

            Element grandParent = getParent().getParent();
            if (parentType.isStruct() && (grandParent.isRoot() || !grandParent.getType().isMapping())) {
                int startIndex = member.getStorageIndex() + toInt(path.removeLast());
                return path.extend(startIndex);
            }

            if (parentType.isStruct() && grandParent.getType().isMapping()) {
                return path.extend(member.getStorageIndex());
            }

            return path.extend(id);
        }

        private List<Integer> arrIndexes() {
            if (!type.isArray()) {
                throw new UnsupportedOperationException("Can't get indexes for non array element.");
            }

            Set<Integer> indexes = new HashSet<>();

            int slotsPerElement = 1;
            if (type.isStructArray()) {
                Ast.Type.Struct structType = type.asArray().getElementType().asStruct();
                slotsPerElement = getStructFields(structType).reservedSlotsCount();
            }

            if (type.isStaticArray()) {
                int offset = member.getStorageIndex();

                if (Member.BYTES_IN_SLOT / Member.size(type.asArray().getElementType()) > 1) {
                    IntStream.range(0, type.asArray().getSize()).forEach(indexes::add);
                } else {
                    int size = type.asArray().getSize() * slotsPerElement;

                    for (StorageDictionary.PathElement child : getParent().toDictionaryPathElement().getChildren()) {
                        int current = toInt(child.key) - offset;

                        if (current >= size) break;
                        if (current < 0) continue;

                        indexes.add(current / slotsPerElement);
                    }
                }
            } else {
                StorageDictionary.PathElement element = toDictionaryPathElement();
                if (element != null) {
                    for (StorageDictionary.PathElement child : element.getChildren()) {
                        int current = toInt(child.key);
                        indexes.add(current / slotsPerElement);
                    }
                }
            }

            return indexes.stream().sorted().collect(toList());
        }

        @Override
        public int getChildrenCount(boolean ignoreEmpty) {
            int result = 0;

            if (type.isContainer()) {
                StorageDictionary.PathElement element = toDictionaryPathElement();
                if (element != null) {
                    result = element.getChildrenCount();
                }
                if (type.isArray()) {
                    result = arrIndexes().size();
                }
            } else if (type.isStruct()) {
                result = getStructFields(type.asStruct()).size();
            }

            return result;
        }

        @Override
        public List<Element> getChildren(int page, int size, boolean ignoreEmpty) {
            List<Element> result = emptyList();

            int offset = page * size;
            int fromIndex = max(0, offset);
            int toIndex = min(getChildrenCount(ignoreEmpty), offset + size);

            if (fromIndex < toIndex) {
                if (type.isStruct()) {
                    result = getStructFields(type.asStruct()).page(page, size).stream()
                            .map(field -> new ElementImpl(field, this))
                            .collect(toList());
                } else if (type.isArray()) {
                    result = arrIndexes().subList(fromIndex, toIndex).stream()
                            .map(i -> new ElementImpl(String.valueOf(i), type.asArray().getElementType(), this))
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

            DataWord value = null;
            StorageDictionary.PathElement pe = toDictionaryPathElement();
            if (nonNull(pe)) {
                value = valueExtractor.apply(new DataWord(pe.storageKey));
                if (nonNull(member)) {
                    value = member.extractValue(value);
                } else {
                    Member parentMember = getParent().getMember();
                    if (parentMember.getType().isStaticArray()) {
                        Ast.Type.Array array = parentMember.getType().asArray();
                        if (array.getSize() > parentMember.reservedSlotsCount()) {
                            int typeSize = Member.size(array.getElementType());
                            int index = toInt(id);

                            value = extractPackedArrEl(value, index, typeSize);
                        }
                    }
                }
            }

            return value;
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
                result = getEnumValueByOrdinal(type.asEnum(), isNull(rawValue) ? 0 : rawValue.intValue());
            } else if (type.isContract() && nonNull(rawValue)) {
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
                    result = !(isNull(rawValue) || rawValue.isZero());
                } else if (elementary.isAddress() && nonNull(rawValue)) {
                    result = toHexString(rawValue.getLast20Bytes());
                } else if (elementary.isNumber()) {
                    result = isNull(rawValue) ? 0 : rawValue.bigIntValue();
                }
            }

            return result;
        }
    }

    private static boolean isDataWord(String input) {
        return DATA_WORD_PATTERN.matcher(input).matches();
    }

    private static DataWord extractPackedArrEl(DataWord slot, int index, int size) {
        byte[] data = slot.getData();
        int offset = (index + 1) % (Member.BYTES_IN_SLOT / size) * size;
        int from = data.length - offset;
        int to = from + size;

        return new DataWord(ArrayUtils.subarray(data, from, to));
    }
}
