package com.ethercamp.storagedict;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.keyvalue.AbstractKeyValue;
import org.ethereum.vm.DataWord;

import java.util.function.Function;

import static com.ethercamp.storagedict.StorageValue.convertTo;

@Slf4j
public class StorageEntry extends AbstractKeyValue<StorageKey, StorageValue> {

    public StorageEntry(StorageKey key, StorageValue value) {
        super(key, value);
    }

    public static StorageEntry dataMember(ContractData dataMembers, ContractData.Member member, StorageDictionary.PathElement pathElement, Function<StorageDictionary.PathElement, DataWord> valueExtractor) {

        StorageKey key = new StorageKey();
        key.setDecoded(true);
        key.setType("data member");
        key.setName(member.getName());
        key.setPath(member.getIndex());


        StorageValue value = StorageValue.decoded(dataMembers, member.getType(), pathElement, valueExtractor);

        return new StorageEntry(key, value);
    }

    public static StorageEntry structField(ContractData dataMembers, ContractData.PathElementType container, ContractData.Member field, StorageDictionary.PathElement pathElement, Function<StorageDictionary.PathElement, DataWord> valueExtractor) {

        StorageKey key = new StorageKey();
        key.setDecoded(true);
        key.setType("field");
        key.setName(field.getName());
        key.setPath(container.path().extend(field.getIndex()).toString());


        StorageValue value = StorageValue.decoded(dataMembers, field.getType(), pathElement, valueExtractor);

        return new StorageEntry(key, value);
    }

    public static StorageEntry containerItem(ContractData dataMembers, ContractData.PathElementType container, StorageDictionary.PathElement pathElement, Function<StorageDictionary.PathElement, DataWord> valueExtractor) {

        Ast.Type type = container.getType();
        Ast.Type itemType = null;
        String typeName = type.getName();
        String name = pathElement.key;

        if (type.isArray()) {
            typeName = "index";
            itemType = type.asArray().getElementType();
        } else if (type.isMapping()) {
            Ast.Type.Mapping mapping = type.asMapping();
            typeName = mapping.getKeyType().getName();
            itemType = mapping.getValueType();
            name = convertTo(dataMembers, mapping.getKeyType(), name);
        }


        StorageKey key = new StorageKey();
        key.setDecoded(true);
        key.setType(typeName);
        key.setName(name);
        key.setPath(container.path().extend(pathElement.key).toString());
        key.setRaw(pathElement.key);


        StorageValue value = StorageValue.decoded(dataMembers, itemType, pathElement, valueExtractor);

        return new StorageEntry(key, value);
    }
}
