package com.ethercamp.contrdata.utils;

import com.ethercamp.contrdata.BaseTest;
import com.ethercamp.contrdata.contract.ContractData;
import com.ethercamp.contrdata.storage.Storage;
import com.ethercamp.contrdata.storage.dictionary.StorageDictionary;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.Getter;
import org.ethereum.datasource.Source;
import org.ethereum.vm.DataWord;
import org.spongycastle.util.encoders.Hex;

import java.util.Map;

public class RealContractResource extends ContractResource {

    private static class TypeRefs {

        public static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<Map<String, String>>() {
        };
        private static final TypeReference<Map<DataWord, DataWord>> DATA_WORD_MAP = new TypeReference<Map<DataWord, DataWord>>() {
        };
    }

    private final String address;
    @Getter(lazy = true)
    private final Storage storageMap = loadStorage();
    @Getter(lazy = true)
    private final StorageDictionary storageDictionary = loadStorageDictionary();
    @Getter(lazy = true)
    private final ContractData contractData = createContractData();

    public RealContractResource(String name, String address) {
        super(name);
        this.address = address;
    }

    public RealContractResource(ContractInfo info) {
        this(info.getName(), info.getAddress());
    }

    @Override
    protected String loadSource() {
        return loader.loadAsString("real", getName(), "contract.sol");
    }

    @Override
    protected Storage loadStorage() {
        return Storage.fromMap(loader.loadCompressedJsonAsObject(TypeRefs.DATA_WORD_MAP, "real", getName(), address, "storage.gz"));
    }

    @Override
    protected StorageDictionary loadStorageDictionary() {
        Map<String, String> map = loader.loadCompressedJsonAsObject(TypeRefs.STRING_MAP, "real", getName(), address, "dictionary.gz");
        Source<byte[], byte[]> ds = new BaseTest.HashMapDBExt();
        map.forEach((key, value) -> ds.put(Hex.decode(key), Hex.decode(value)));
        return new StorageDictionary(ds);
    }
}
