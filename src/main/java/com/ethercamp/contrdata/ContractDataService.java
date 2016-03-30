package com.ethercamp.contrdata;

import com.ethercamp.contrdata.contract.ContractData;
import com.ethercamp.contrdata.storage.Path;
import com.ethercamp.contrdata.storage.Storage;
import com.ethercamp.contrdata.storage.StorageEntry;
import com.ethercamp.contrdata.storage.StoragePage;
import com.ethercamp.contrdata.storage.dictionary.StorageDictionary;
import com.ethercamp.contrdata.storage.dictionary.StorageDictionaryDb;
import org.ethereum.vm.DataWord;
import org.spongycastle.util.encoders.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

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
        return getStructuredStorageEntries(Hex.decode(address), path, page, size);
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

        return getContractData(addr, contractData, path, page, size);
    }
}
