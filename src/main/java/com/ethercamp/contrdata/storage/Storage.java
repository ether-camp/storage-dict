package com.ethercamp.contrdata.storage;

import org.ethereum.core.Repository;
import org.ethereum.vm.DataWord;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Objects.nonNull;

public interface Storage {

    int size(byte[] address);

    Map<DataWord, DataWord> entries(byte[] address, List<DataWord> keys);

    Set<DataWord> keys(byte[] address);

    DataWord get(byte[] address, DataWord key);

    static Storage fromMap(Map<DataWord, DataWord> map) {
        return new Storage() {
            @Override
            public int size(byte[] address) {
                return map.size();
            }

            @Override
            public Map<DataWord, DataWord> entries(byte[] address, List<DataWord> keys) {
                Map<DataWord, DataWord> result = new HashMap<>();
                for (DataWord key : keys) {
                    DataWord value = map.get(key);
                    if (nonNull(value)) {
                        result.put(key, value);
                    }
                }
                return result;
            }

            @Override
            public Set<DataWord> keys(byte[] address) {
                return map.keySet();
            }

            @Override
            public DataWord get(byte[] address, DataWord key) {
                return map.get(key);
            }
        };
    }

    static Storage fromRepo(Repository repository) {
        return new Storage() {
            @Override
            public int size(byte[] address) {
                return repository.getContractDetails(address).getStorageSize();
            }

            @Override
            public Map<DataWord, DataWord> entries(byte[] address, List<DataWord> keys) {
                return repository.getContractDetails(address).getStorage(keys);
            }

            @Override
            public Set<DataWord> keys(byte[] address) {
                return repository.getContractDetails(address).getStorageKeys();
            }

            @Override
            public DataWord get(byte[] address, DataWord key) {
                return repository.getContractDetails(address).get(key);
            }
        };
    }
}
