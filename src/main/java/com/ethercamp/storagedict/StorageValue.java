package com.ethercamp.storagedict;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.DataWord;
import org.spongycastle.util.encoders.Hex;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

import static java.lang.String.format;
import static org.apache.commons.collections4.CollectionUtils.size;
import static org.apache.commons.lang3.ArrayUtils.getLength;
import static org.apache.commons.lang3.ArrayUtils.subarray;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.ethereum.util.ByteUtil.toHexString;
import static org.spongycastle.util.encoders.Hex.decode;

@Data
@Slf4j
@NoArgsConstructor
public class StorageValue {

    public enum Type {
        address("^0{24,30}[^0][0-9a-fA-F]+"),
        string("[0-9a-fA-F]+[^0]0{14,62}"),
        number("^0{48}[0-9a-fA-F]+"),
        data,
        map,
        array,
        struct;

        private String matcher;

        Type(String matcher) {
            this.matcher = matcher;
        }

        Type() {
            this(EMPTY);
        }

        private boolean matches(String raw) {
            return isNotBlank(matcher) && raw.matches(matcher);
        }

        private static Set<Type> containerTypes;

        static {
            containerTypes = new HashSet<Type>() {{
                add(map);
                add(array);
                add(struct);
            }};
        }

        public static boolean isContainer(Type type) {
            return containerTypes.contains(type);
        }

        public static Type resolve(StorageDictionary.PathElement.Type childrenType, DataWord value) {
            try {
                Type result;

                if (childrenType == null) {
                    String raw = value.toString();
                    result = Arrays.stream(values())
                            .filter(t -> t.matches(raw))
                            .findFirst()
                            .orElseGet(() -> data);
                } else {
                    switch (childrenType) {
                        case MapKey:
                            result = map;
                            break;
                        case ArrayIndex:
                            result = array;
                            break;
                        case Offset:
                            result = struct;
                            break;
                        default:
                            result = data;
                    }
                }

                return result;
            } catch (Exception e) {
                return data;
            }
        }
    }

    private String raw;
    private String value;
    /*
        private String asAddress;
        private String asString;
        private String asNumber;
    */
    private String type;
    private boolean container;
    private int size;

    private boolean decoded;
    private String kind;

    public StorageValue(DataWord value, Type type, int size) {
        this.raw = Objects.toString(value, EMPTY);
        this.type = type.name();
        this.value = formatAs(type, value);
/*
        this.asAddress = formatAs(Type.address, raw);
        this.asString = formatAs(Type.string, raw);
        this.asNumber = formatAs(Type.number, raw);
*/
        this.container = Type.isContainer(type);
        this.size = size;
    }

    private String formatAs(Type type, DataWord value) {
        String result = EMPTY;
        if (value != null) {
            switch (type) {
                case address:
                    result = toHexString(value.getLast20Bytes());
                    break;
                case string:
                    String asHex = value.toString().replaceAll("0*$", "");
                    try {
                        result = new String(decode(asHex));
                    } catch (Exception e) {
                        log.warn(format("Cannot format storage value[%s] as string: ", value), e);
                        result = EMPTY;
                    }
                    break;
                case number:
                    result = value.bigIntValue();
                    break;
                case data:
                    result = value.toString();
                    break;
            }
        }
        return result;
    }

    public static StorageValue create(StorageDictionary.PathElement pathElement, Function<StorageDictionary.PathElement, DataWord> valueExtractor) {
        int size = pathElement.getChildrenCount();
        StorageDictionary.PathElement.Type childrenType = null;
        DataWord value = null;

        if (size > 0) {
            childrenType = pathElement.getChildren().iterator().next().type;
        } else {
            value = valueExtractor.apply(pathElement);
        }

        return new StorageValue(value, Type.resolve(childrenType, value), size);
    }

    public static StorageValue decoded(ContractData contractData, Ast.Type type, StorageDictionary.PathElement pathElement, Function<StorageDictionary.PathElement, DataWord> valueExtractor) {
        StorageValue value = new StorageValue();
        value.setDecoded(true);
        value.setType(type.formatName());
        value.setContainer(!(type.isElementary() || type.isEnum() || type.isContract()));
        value.setKind(type.getName());

        if (value.isContainer()) {

            int childrenCount = (pathElement == null) ? 0 : pathElement.getChildrenCount();
            if (type.isStructArray()) {
                List<ContractData.Member> fields = contractData.getStructureFields(type.asArray());
                childrenCount = childrenCount / size(fields);
            } else if (type.isStruct()) {
                List<ContractData.Member> fields = contractData.getStructureFields(type.asStruct());
                childrenCount = size(fields);
            } else if (type.isStaticArray()) {
                childrenCount = (childrenCount > 0) ? childrenCount : type.asArray().getSize();
            }
            value.setSize(childrenCount);

        } else {
            DataWord rawValue = valueExtractor.apply(pathElement);
            Supplier<byte[]> bytesSupplier = () -> (pathElement == null) ? ArrayUtils.EMPTY_BYTE_ARRAY : getBytes(pathElement, valueExtractor);

            value.setValue(convertTo(contractData, type, rawValue, bytesSupplier));
            value.setRaw(Objects.toString(rawValue, null));
        }

        return value;
    }

    public static String convertTo(ContractData contractData, Ast.Type type, DataWord rawValue, Supplier<byte[]> bytesSupplier) {
        Object result = rawValue;

        if (type.isEnum()) {
            result = contractData.getEnumValueByOrdinal(type.asEnum(), (rawValue == null) ? 0 : rawValue.intValue());
        } else if (type.isElementary()) {
            Ast.Type.Elementary elementary = type.asElementary();
            if (elementary.isString()) {
                byte[] bytes = bytesSupplier.get();
                if (getLength(bytes) == 32) {
                    bytes = subarray(bytes, 0, bytes.length - 1);
                }
                result = new String(bytes);
            } else if (elementary.is("bytes")) {
                result = Hex.toHexString(bytesSupplier.get());
            } else if (elementary.isBool()) {
                result = !(rawValue == null || rawValue.isZero());
            } else if (elementary.isAddress() && rawValue != null) {
                result = toHexString(rawValue.getLast20Bytes());
            } else if (elementary.isNumber()) {
                result = (rawValue == null) ? 0 : rawValue.bigIntValue();
            }
        }

        return Objects.toString(result, null);
    }

    public static String convertTo(ContractData contractData, Ast.Type type, String decoded) {
        Ast.Type.Elementary elementary = type.asElementary();
        DataWord rawValue = elementary.isString() ? new DataWord(decoded.getBytes()) : new DataWord(decoded);
        return convertTo(contractData, type, rawValue, () -> decoded.getBytes());
    }

    private static byte[] getBytes(StorageDictionary.PathElement pathElement, Function<StorageDictionary.PathElement, DataWord> valueExtractor) {
        byte[] result;

        if (pathElement.hasChildren()) {
            byte[][] strPartsBytes = pathElement.getChildrenStream()
                    .map(valueExtractor)
                    .filter(Objects::nonNull)
                    .map(DataWord::getData)
                    .toArray(byte[][]::new);

            if (ArrayUtils.isEmpty(strPartsBytes)) {
                DataWord strValue = valueExtractor.apply(pathElement);
                result = (strValue == null) ? ArrayUtils.EMPTY_BYTE_ARRAY : strValue.getData();
            } else {
                result = ByteUtil.merge(strPartsBytes);
            }
        } else {
            DataWord strValue = valueExtractor.apply(pathElement);
            result = (strValue == null) ? ArrayUtils.EMPTY_BYTE_ARRAY : strValue.getData();
        }

        return result;
    }
}