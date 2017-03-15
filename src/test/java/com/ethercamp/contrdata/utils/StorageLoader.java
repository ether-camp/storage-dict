package com.ethercamp.contrdata.utils;

import com.ethercamp.contrdata.BaseTest;
import com.ethercamp.contrdata.storage.Storage;
import com.ethercamp.contrdata.storage.dictionary.StorageDictionary;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.collections4.keyvalue.DefaultKeyValue;
import org.apache.http.client.fluent.Request;
import org.ethereum.datasource.Source;
import org.ethereum.vm.DataWord;
import org.spongycastle.util.encoders.Hex;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.lang.String.format;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class StorageLoader {

    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Page<T> {
        private List<T> content;
        private boolean last;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StorageEntry extends DefaultKeyValue<DataWord, DataWord> {

    }

    public static class StoragePage extends Page<StorageEntry> {

        public Storage toStorage() {
            Map<DataWord, DataWord> storage = toMap();
            return new Storage() {
                @Override
                public int size(byte[] address) {
                    return storage.size();
                }

                @Override
                public Map<DataWord, DataWord> entries(byte[] address, List<DataWord> keys) {
                    return storage.entrySet().stream()
                            .filter(e -> keys.contains(e.getKey()))
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                }

                @Override
                public Set<DataWord> keys(byte[] address) {
                    return storage.keySet();
                }

                @Override
                public DataWord get(byte[] address, DataWord key) {
                    return storage.get(key);
                }
            };
        }

        public Map<DataWord, DataWord> toMap() {
            return getContent().stream().collect(Collectors.toMap(StorageEntry::getKey, StorageEntry::getValue));
        }

        public static StoragePage load(final String stateServiceUrl, String address, int page, int size) throws IOException {
            String uri = format("%s/api/v1/accounts/%s/storage?page=%d&size=%d", stateServiceUrl, address, page, size);
            System.out.println("Storage loading " + uri + " ...");
            return Request.Get(uri)
                    .execute()
                    .handleResponse(response -> OBJECT_MAPPER.readValue(response.getEntity().getContent(), StoragePage.class));
        }
        public static StoragePage loadAll(final String stateServiceUrl, String address) throws IOException {
            return load(stateServiceUrl, address, 0, Integer.MAX_VALUE);
        }
    }

    public static Map<String, String> loadStorageDictionaryDump(final String stateServiceUrl, String address) throws IOException {
        String uri = format("%s/api/v1/accounts/%s/smart-storage/export", stateServiceUrl, address);
        System.out.println("Storage dictionary loading " + uri + " ...");
        return Request.Get(uri)
                .execute()
                .handleResponse(response -> {
                    InputStream content = response.getEntity().getContent();
                    return new ObjectMapper().readValue(content, new TypeReference<Map<String, String>>() {
                    });
                });
    }

    public static StorageDictionary loadStorageDictionary(final String stateServiceUrl, String address) throws IOException {
        Map<String, String> dump = loadStorageDictionaryDump(stateServiceUrl, address);
        Source<byte[], byte[]> ds = new BaseTest.HashMapDBExt();
        dump.forEach((k, v) -> ds.put(Hex.decode(k), Hex.decode(v)));
        return new StorageDictionary(ds);
    }
}
