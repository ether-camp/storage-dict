package com.ethercamp.contrdata;

import com.ethercamp.contrdata.contract.ContractData;
import com.ethercamp.contrdata.storage.Path;
import com.ethercamp.contrdata.storage.Storage;
import com.ethercamp.contrdata.storage.StorageEntry;
import com.ethercamp.contrdata.storage.StoragePage;
import com.ethercamp.contrdata.storage.dictionary.StorageDictionary;
import com.ethercamp.contrdata.storage.dictionary.StorageDictionaryDb;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.ethereum.datasource.KeyValueDataSource;
import static org.ethereum.util.ByteUtil.toHexString;
import org.ethereum.vm.DataWord;
import org.spongycastle.util.encoders.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

@Slf4j
@Service
public class ContractDataService {

    @Autowired
    private StorageDictionaryDb dictionaryDb;
    @Autowired
    private Storage storage;

    public StoragePage getStorageEntries(byte[] address, int page, int size) {
        List<StorageEntry> entries = emptyList();

        int storageSize = storage.size(address);
        if (storageSize > 0) {
            int offset = page * size;
            int fromIndex = max(offset, 0);
            int toIndex = min(storageSize, offset + size);

            if (fromIndex < toIndex) {
                List<DataWord> keys = storage.keys(address).stream()
                        .sorted()
                        .collect(toList())
                        .subList(fromIndex, toIndex);

                entries = storage.entries(address, keys).entrySet().stream()
                        .map(StorageEntry::raw)
                        .sorted()
                        .collect(toList());
            }
        }

        return new StoragePage(entries, page, size, storageSize);
    }

    public StoragePage getStructuredStorageEntries(byte[] address, Path path, int page, int size) {
        StorageDictionary dictionary = getDictionary(address).getFiltered(storage.keys(address));

        StorageDictionary.PathElement pathElement = dictionary.getByPath(path.parts());
        List<StorageEntry> entries = pathElement
                .getChildren(page * size, size).stream()
                .map(pe -> StorageEntry.structured(pe, key -> storage.get(address, key)))
                .collect(toList());

        return new StoragePage(entries, page, size, pathElement.getChildrenCount());
    }

    private StorageDictionary getDictionary(byte[] address) {
        return dictionaryDb.getOrCreate(StorageDictionaryDb.Layout.Solidity, address);
    }

    public StoragePage getStructuredStorageEntries(String address, Path path, int page, int size) {
        byte[] addr = Hex.decode(address);
        try {
            return getStructuredStorageEntries(addr, path, page, size);
        } catch (Exception e) {
            log.error("Cannot build smart contract data:\n" +
                            "address: {}\n" +
                            "path: {}\n" +
                            "storage dictionary:\n{}\n" +
                            "storage:\n{}",
                    address, path, dumpDict(getDictionary(addr)), dumpStorage(addr));
            throw e;
        }
    }

    public StoragePage getContractData(byte[] address, ContractData contractData, Path path, int page, int size) {
        ContractData.Element element = contractData.elementByPath(path.parts());
        List<StorageEntry> entries = element
                .getChildren(page, size).stream()
                .map(el -> StorageEntry.smart(el, key -> storage.get(address, key)))
                .collect(toList());

        return new StoragePage(entries, page, size, element.getChildrenCount());
    }

    public StoragePage getContractData(String address, String contractDataJson, Path path, int page, int size) {
        byte[] addr = Hex.decode(address);
        StorageDictionary dictionary = getDictionary(addr).getFiltered(storage.keys(addr));
        ContractData contractData = ContractData.parse(contractDataJson, dictionary);

        try {
            return getContractData(addr, contractData, path, page, size);
        } catch (Exception e) {
            log.error("Cannot build smart contract data:\n" +
                    "address: {}\n" +
                    "path: {}\n" +
                    "contract data-members:\n{}\n" +
                    "storage dictionary:\n{}\n" +
                    "storage:\n{}",
                    address, path, contractDataJson, dumpDict(dictionary), dumpStorage(addr));
            throw e;
        }
    }

    private String dumpDict(StorageDictionary dictionary) {
        KeyValueDataSource dictDb = dictionary.getStorageDb();
        Map<String, String> entries = dictDb.keys().stream()
                .collect(toMap(key -> toHexString(key), key -> toHexString(dictDb.get(key))));
        try {
            return new ObjectMapper().writeValueAsString(entries);
        } catch (JsonProcessingException e) {
            return "Cannot storage dictionary dump ...";
        }
    }

    private String dumpStorage(byte[] address) {
        Set<DataWord> keys = storage.keys(address);
        Map<DataWord, DataWord> entries = storage.entries(address, new ArrayList<>(keys));
        try {
            return new ObjectMapper().writeValueAsString(entries);
        } catch (JsonProcessingException e) {
            return "Cannot create storage dump ...";
        }
    }
}
