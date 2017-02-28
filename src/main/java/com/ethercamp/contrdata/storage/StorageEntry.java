package com.ethercamp.contrdata.storage;

import com.ethercamp.contrdata.contract.Ast;
import com.ethercamp.contrdata.contract.ContractData;
import com.ethercamp.contrdata.storage.dictionary.StorageDictionary;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.apache.commons.collections4.keyvalue.AbstractKeyValue;
import org.apache.commons.lang3.StringUtils;
import org.ethereum.vm.DataWord;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import static org.ethereum.util.ByteUtil.toHexString;

@EqualsAndHashCode
public class StorageEntry extends AbstractKeyValue implements Comparable<StorageEntry> {

    @Getter
    @Builder
    @EqualsAndHashCode
    public static class Key implements Comparable<Key> {
        private String encoded;
        private String decoded;
        private String kind;
        private String path;

        @Override
        public int compareTo(Key another) {
            return encoded.compareTo(another.getEncoded());
        }
    }

    @Getter
    @Builder
    @EqualsAndHashCode
    public static class Value {
        private String encoded;
        private String decoded;
        private String type;
        private String typeKind;
        private boolean container;
        private int size;
    }

    public enum Type {
        raw, structured, smart
    }

    @Getter
    private final Type type;

    private StorageEntry(Type type, Object key, Object value) {
        super(key, value);
        this.type = type;
    }

    @Override
    public int compareTo(StorageEntry another) {
        Object key = getKey();

        if (key instanceof Comparable) {
            return ((Comparable) key).compareTo(another.getKey());
        } else {
            return 0;
        }
    }

    private static String resolveKind(ContractData.Element el) {
        String result = null;

        if (el.getParent().isRoot()) {
            result = "data member";
        } else {
            Ast.Type prevElType = el.getParent().getType();
            if (prevElType.isArray()) {
                result = "index";
            } else if (prevElType.isStruct()) {
                result = "field";
            } else if (prevElType.isMapping()) {
                result = prevElType.asMapping().getKeyType().formatName();
            }
        }

        return result;
    }

    private static String resolveKeyKind(StorageDictionary.PathElement element) {
        switch (element.type) {
            case StorageIndex:
            case MapKey:
                return "key";
            case ArrayIndex:
                return "index";
            case Offset:
                return "offset";
            default:
                return StringUtils.EMPTY;
        }
    }

    private static String resolveValueKind(StorageDictionary.PathElement element) {
        String result = "data";
        if (element.hasChildren()) {
            switch (element.getFirstChild().type) {
                case MapKey:
                    result = "map";
                    break;
                case ArrayIndex:
                    result = "array";
                    break;
                case Offset:
                    result = "struct";
                    break;
            }
        }

        return result;
    }

    public static StorageEntry raw(Map.Entry<DataWord, DataWord> entry) {
        return new StorageEntry(Type.raw, entry.getKey(), entry.getValue());
    }

    public static StorageEntry structured(StorageDictionary.PathElement pe, Function<DataWord, DataWord> valueExtractor) {
        Key.KeyBuilder key = Key.builder()
                .kind(resolveKeyKind(pe))
                .encoded(toHexString(pe.storageKey))
                .decoded(pe.key)
                .path(Path.of(pe.getFullPath()).toString());

        Value.ValueBuilder value = Value.builder()
                .typeKind(resolveValueKind(pe))
                .container(pe.hasChildren())
                .size(pe.getChildrenCount());

        if (!pe.hasChildren()) {
            DataWord storageValue = valueExtractor.apply(new DataWord(pe.storageKey));
            value.encoded(Objects.toString(storageValue, null));
        }

        return new StorageEntry(Type.structured, key.build(), value.build());
    }

    public static StorageEntry smart(ContractData.Element cde, Function<DataWord, DataWord> valueExtractor) {
        StorageDictionary.PathElement pathElement = cde.toDictionaryPathElement();

        Key.KeyBuilder key = Key.builder()
                .kind(resolveKind(cde))
                .encoded(pathElement == null ? null : toHexString(pathElement.storageKey))
                .decoded(cde.getKey())
                .path(cde.path().toString());

        Ast.Type type = cde.getType();

        Value.ValueBuilder value = Value.builder()
                .type(type.formatName())
                .typeKind(type.isUserDefined() ? type.getName() : null)
                .container(type.isContainer());

        if (type.isContainer()) {
            value.size(cde.getChildrenCount());
        } else if (!type.isStruct()) {
            value
                    .encoded(Objects.toString(cde.getStorageValue(valueExtractor), null))
                    .decoded(cde.getValue(valueExtractor));
        }

        return new StorageEntry(Type.smart, key.build(), value.build());
    }
}
