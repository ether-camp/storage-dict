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
import org.apache.commons.lang3.StringUtils;
import org.ethereum.datasource.KeyValueDataSource;

import static java.lang.String.format;
import static java.util.stream.Collectors.joining;
import static org.ethereum.util.ByteUtil.toHexString;

import org.ethereum.vm.DataWord;
import org.spongycastle.util.encoders.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

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

    public StoragePage getStructuredStorageEntries(byte[] address, StorageDictionary dictionary, Path path, int page, int size) {

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
            return getStructuredStorageEntries(addr, getDictionary(addr).getFiltered(storage.keys(addr)), path, page, size);
        } catch (Exception e) {
            log.error(DetailedMsg.withTitle("Cannot build contract structured storage:")
                    .add("address",address)
                    .add("path",path)
                    .add("storage dictionary", dumpDict(getDictionary(addr)))
                    .add("storage", dumpStorage(addr))
                    .toString());
            throw e;
        }
    }

    public StoragePage getStructuredStorageDiffEntries(String txHash, String address, Path path, int page, int size) {
        byte[] addr = Hex.decode(address);
        try {
            byte[] hash = Hex.decode(txHash);
            StorageDictionary dictionary = getDictionary(addr).getFiltered(storage.keys(hash));
            return getStructuredStorageEntries(hash, dictionary, path, page, size);
        } catch (Exception e) {
            log.error(DetailedMsg.withTitle("Cannot build contract structured storage:")
                    .add("address",address)
                    .add("transaction hash", txHash)
                    .add("path",path)
                    .add("storage dictionary", dumpDict(getDictionary(addr)))
                    .add("storage", dumpStorage(addr))
                    .toString());
            throw e;
        }
    }

    public StoragePage getContractData(byte[] address, ContractData contractData, boolean ignoreEmpty, Path path, int page, int size) {
        ContractData.Element element = contractData.elementByPath(path.parts());
        List<StorageEntry> entries = element.getChildren(page, size, ignoreEmpty).stream()
                .map(el -> StorageEntry.smart(el, key -> storage.get(address, key)))
                .collect(toList());

        return new StoragePage(entries, page, size, element.getChildrenCount());
    }

    public StoragePage getContractData(String address, String contractDataJson, Path path, int page, int size) {
        byte[] addr = Hex.decode(address);
        StorageDictionary dictionary = getDictionary(addr).getFiltered(storage.keys(addr));
        ContractData contractData = ContractData.parse(contractDataJson, dictionary);

        try {
            return getContractData(addr, contractData, false, path, page, size);
        } catch (Exception e) {
            log.error(DetailedMsg.withTitle("Cannot build smart contract data:")
                    .add("address", address)
                    .add("path", path)
                    .add("contract data-members", contractDataJson)
                    .add("storage dictionary", dumpDict(dictionary))
                    .add("storage", dumpStorage(addr))
                    .toString());
            throw e;
        }
    }

    public StoragePage getContractDataDiff(String txHash, String address, String contractDataJson, Path path, int page, int size) {
        byte[] hash = Hex.decode(txHash);
        StorageDictionary dictionary = getDictionary(Hex.decode(address)).getFiltered(storage.keys(hash));
        ContractData contractData = ContractData.parse(contractDataJson, dictionary);

        try {
            return getContractData(hash, contractData, true, path, page, size);
        } catch (Exception e) {
            log.error(DetailedMsg.withTitle("Cannot build smart contract data:")
                    .add("address", address)
                    .add("transaction hash", txHash)
                    .add("path", path)
                    .add("contract data-members", contractDataJson)
                    .add("storage dictionary", dumpDict(dictionary))
                    .add("storage", dumpStorage(hash))
                    .toString());
            throw e;
        }
    }

    private static class DetailedMsg extends LinkedHashMap<String, Object> {

        public static DetailedMsg withTitle(String title, Object... args) {
            return new DetailedMsg().add(format(title, args), StringUtils.EMPTY);
        }

        public DetailedMsg add(String title, Object value) {
            put(title, value);
            return this;
        }

        @Override
        public String toString() {
            return entrySet().stream()
                    .map(entry -> entry.getKey() + ": " + Objects.toString(entry.getValue()))
                    .collect(joining("\n"));
        }
    }

    public String dumpDict(StorageDictionary dictionary) {
        KeyValueDataSource dictDb = dictionary.getStorageDb();
        Map<String, String> entries = dictDb.keys().stream()
                .collect(toMap(key -> toHexString(key), key -> toHexString(dictDb.get(key))));
        try {
            return new ObjectMapper().writeValueAsString(entries);
        } catch (JsonProcessingException e) {
            return "Cannot storage dictionary dump ...";
        }
    }

    public String dumpStorage(byte[] address) {
        Set<DataWord> keys = storage.keys(address);
        Map<DataWord, DataWord> entries = storage.entries(address, new ArrayList<>(keys));
        try {
            return new ObjectMapper().writeValueAsString(entries);
        } catch (JsonProcessingException e) {
            return "Cannot create storage dump ...";
        }
    }
}
