package com.ethercamp.contrdata;

import com.ethercamp.contrdata.contract.Ast;
import com.ethercamp.contrdata.contract.ContractData;
import com.ethercamp.contrdata.contract.Member;
import com.ethercamp.contrdata.contract.Members;
import com.ethercamp.contrdata.storage.Path;
import com.ethercamp.contrdata.storage.Storage;
import com.ethercamp.contrdata.storage.StorageEntry;
import com.ethercamp.contrdata.storage.StoragePage;
import com.ethercamp.contrdata.storage.dictionary.Layout;
import com.ethercamp.contrdata.storage.dictionary.StorageDictionary;
import com.ethercamp.contrdata.storage.dictionary.StorageDictionaryDb;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.ethereum.datasource.Source;
import org.ethereum.vm.DataWord;
import org.spongycastle.util.encoders.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.ethereum.util.ByteUtil.intToBytes;
import static org.ethereum.util.ByteUtil.toHexString;

@Slf4j(topic = "contract-data")
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

    private StorageDictionary getDictionary(byte[] address) {
        return dictionaryDb.getDictionaryFor(Layout.Lang.solidity, address);
    }

    private StorageDictionary getDictionary(byte[] address, Set<DataWord> hashFilter) {
        return getDictionary(address).getFiltered(hashFilter);
    }

    public StoragePage getStructuredStorageEntries(byte[] address, StorageDictionary dictionary, Path path, int page, int size) {

        try {
            StorageDictionary.PathElement pathElement = dictionary.getByPath(path.parts());
            List<StorageEntry> entries = pathElement
                    .getChildren(page * size, size).stream()
                    .map(pe -> StorageEntry.structured(pe, key -> storage.get(address, key)))
                    .collect(toList());

            return new StoragePage(entries, page, size, pathElement.getChildrenCount());
        } catch (Exception e) {
            log.error(DetailedMsg.withTitle("Cannot build contract structured storage:")
                    .add("address",address)
                    .add("path",path)
                    .add("storageDictionary", dictionary.dmp())
                    .add("storage", storageEntries(address))
                    .toJson());
            throw e;
        }
    }

    public Map<String, String> exportDictionary(byte[] address, Path path) {
        Map<String, String> result = new HashMap<>();

        StorageDictionary.PathElement pathElement = getDictionary(address).getByPath(path.parts());
        StorageDictionary.dmp(pathElement, result);

        return result;
    }

    public void importDictionary(byte[] address, Map<String, String> toImport) {
        clearDictionary(address);
        StorageDictionary dictionary = dictionaryDb.getDictionaryFor(Layout.Lang.solidity, address);
        try {
            Source<byte[], byte[]> dataSource = dictionary.getStorageDb();
            toImport.forEach((key, value) -> dataSource.put(Hex.decode(key), Hex.decode(value)));
        } finally {
            dictionaryDb.flush();
        }
    }

    public void clearDictionary(byte[] address) {
        StorageDictionary dictionary = dictionaryDb.getDictionaryFor(Layout.Lang.solidity, address);
        try {
            Source<byte[], byte[]> source = dictionary.getStorageDb();
            dictionary.allKeys().forEach(key -> source.delete(key.getData()));
        } finally {
            dictionaryDb.flush();
        }
    }

    public StoragePage getStructuredStorageEntries(String address, Path path, int page, int size) {
        byte[] addr = Hex.decode(address);
        StorageDictionary dictionary = getDictionary(addr);

        return getStructuredStorageEntries(addr, dictionary, path, page, size);
    }

    public StoragePage getStructuredStorageDiffEntries(String transactionHash, String address, Path path, int page, int size) {
        byte[] contractAddress = Hex.decode(address);
        byte[] txHash = Hex.decode(transactionHash);
        StorageDictionary dictionary = getDictionary(contractAddress, storage.keys(txHash));

        return getStructuredStorageEntries(txHash, dictionary, path, page, size);
    }

    public StoragePage getContractData(byte[] address, ContractData contractData, boolean ignoreEmpty, Path path, int page, int size) {
        try {
            ContractData.Element element = contractData.elementByPath(path.parts());
            List<StorageEntry> entries = element.getChildren(page, size, ignoreEmpty).stream()
                    .map(el -> StorageEntry.smart(el, key -> storage.get(address, key)))
                    .collect(toList());

            return new StoragePage(entries, page, size, element.getChildrenCount());
        } catch (Exception e) {
            log.error(DetailedMsg.withTitle("Cannot build smart contract data:")
                    .add("address", address)
                    .add("path", path)
                    .add("dataMembers", contractData.getContract())
                    .add("storageDictionary", contractData.getDictionary().dmp())
                    .add("storage", storageEntries(address))
                    .toJson());
            throw e;
        }
    }

    public StoragePage getContractData(String address, String contractDataJson, Path path, int page, int size) {
        byte[] contractAddress = Hex.decode(address);
        StorageDictionary dictionary = getDictionary(contractAddress);
        ContractData contractData = ContractData.parse(contractDataJson, dictionary);

        return getContractData(contractAddress, contractData, false, path, page, size);
    }

    /**
     * Fill dictionary with missing properties.
     * Useful when if indexing started not from zero block
     * (for example after fast sync).
     *
     * Changes aren't get persisted after this operation.
     */
    public void fillMissingKeys(ContractData contractData) {
        final StorageDictionary.PathElement root = contractData.getDictionary().getByPath();

        fillKeys(contractData, root, contractData.getMembers(), 0);
    }

    private static void fillKeys(ContractData contractData, StorageDictionary.PathElement root, List<Member> members, int addition) {
        members.forEach(member -> {
            if (member.getType().isElementary()) {
                final StorageDictionary.PathElement pe = new StorageDictionary.PathElement();
                pe.type = StorageDictionary.PathElement.Type.StorageIndex;
                pe.key = String.valueOf(member.getStorageIndex() + addition);
                pe.storageKey = new DataWord(intToBytes(member.getStorageIndex() + addition)).getData();

                root.addChild(pe);

            } else if (member.getType().isStruct()) {
                final Ast.Type.Struct struct = member.getType().asStruct();

                final Members structFields = contractData.getStructFields(struct);
                fillKeys(contractData, root, structFields, member.getStorageIndex() + addition);
            } else if (member.getType().isStaticArray()) {
                final Ast.Type.Array array = member.getType().asArray();

                for (int i = 0; i < array.getSize(); i++) {
                    final Ast.Variable variable = new Ast.Variable();
                    variable.setType(array.getElementType());
                    Member subMember = new Member(null, variable, contractData);
                    fillKeys(contractData, root, Arrays.asList(subMember), member.getStorageIndex() + i);
                }
            } else {
                // ignore dynamic arrays and mappings
            }
        });
    }

    public StoragePage getContractDataDiff(String transactionHash, String address, String contractDataJson, Path path, int page, int size) {
        byte[] contractAddress = Hex.decode(address);
        byte[] txHash = Hex.decode(transactionHash);
        StorageDictionary dictionary = getDictionary(contractAddress, storage.keys(txHash));
        ContractData contractData = ContractData.parse(contractDataJson, dictionary);

        return getContractData(txHash, contractData, true, path, page, size);
    }

    private static class DetailedMsg extends LinkedHashMap<String, Object> {

        public static DetailedMsg withTitle(String title, Object... args) {
            return new DetailedMsg().add("title", format(title, args));
        }

        public DetailedMsg add(String title, Object value) {
            put(title, value);
            return this;
        }

        public DetailedMsg add(String title, byte[] bytes) {
            return add(title, toHexString(bytes));
        }

        public String toJson() {
            try {
                return new ObjectMapper().writeValueAsString(this);
            } catch (JsonProcessingException e) {
                return "Cannot format JSON message cause: " + e.getMessage();
            }
        }

        @Override
        public String toString() {
            return entrySet().stream()
                    .map(entry -> entry.getKey() + ": " + Objects.toString(entry.getValue()))
                    .collect(joining("\n"));
        }
    }

    public Map<DataWord, DataWord> storageEntries(byte[] address) {
        Set<DataWord> keys = storage.keys(address);
        return storage.entries(address, new ArrayList<>(keys));
    }
}
