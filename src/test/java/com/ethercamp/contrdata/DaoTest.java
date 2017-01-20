package com.ethercamp.contrdata;

import com.ethercamp.contrdata.config.ContractDataConfig;
import com.ethercamp.contrdata.contract.Ast;
import com.ethercamp.contrdata.storage.Path;
import com.ethercamp.contrdata.storage.Storage;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.collections4.keyvalue.DefaultKeyValue;
import org.apache.http.client.fluent.Request;
import org.ethereum.datasource.leveldb.LevelDbDataSource;
import org.ethereum.vm.DataWord;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.lang.String.format;
import static java.time.Duration.between;
import static java.time.Instant.now;
import static java.util.stream.Collectors.toMap;

@ContextConfiguration(loader = AnnotationConfigContextLoader.class, classes = {
        ContractDataConfig.class, BaseTest.Config.class, DaoTest.Config.class
})
@Ignore("Original storage may be loaded")
public class DaoTest extends BaseTest {

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Page<T> {
        private List<T> content;
        private boolean last;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class StorageEntry extends DefaultKeyValue<DataWord, DataWord> {

    }

    private static class StoragePage extends Page<StorageEntry> {

        public Map<DataWord, DataWord> toMap() {
            return getContent().stream().collect(Collectors.toMap(StorageEntry::getKey, StorageEntry::getValue));
        }

        public static StoragePage load(String address, int page, int size) throws IOException {
            System.out.println("loading " + page + " page ...");
            return Request.Get(format("https://state.ether.camp/api/v1/accounts/%s/storage?page=%d&size=%d", address, page, size))
                    .execute()
                    .handleResponse(response -> new ObjectMapper().readValue(response.getEntity().getContent(), StoragePage.class));
        }
    }

    @Configuration
    static class Config {

//        public static final String DAO_ADDRESS = "aeef46db4855e25702f8237e8f403fddcaf931c0";
//        public static final String DAO_ADDRESS = "aeef46db4855e25702f8237e8f403fddcaf931c0";
        public static final String DAO_ADDRESS = "bb9bc244d798123fde783fcc1c72d3bb8c189413";
        public static final String STORAGE_DIR = "/home/eshevchenko/projects/git/storage-dict/database/storage";

        private static Map<DataWord, DataWord> loadLiveStorage(String address) throws IOException {
            Map<DataWord, DataWord> result = new HashMap<>();

            int pageNumber = 0;
            final int pageSize = 2000;

            StoragePage storagePage = StoragePage.load(address, pageNumber, pageSize);
            result.putAll(storagePage.toMap());
            while (!storagePage.isLast()) {
                storagePage = StoragePage.load(address, ++pageNumber, pageSize);
                result.putAll(storagePage.toMap());
            }

            return result;
        }

        private static Map<DataWord, DataWord> loadStorage(String address) throws IOException {
            Map<DataWord, DataWord> storage;

            ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
            java.nio.file.Path storagePath = Paths.get(STORAGE_DIR, address);
            if (Files.exists(storagePath)) {
                storage = mapper.readValue(storagePath.toFile(), new TypeReference<Map<DataWord, DataWord>>() { });
            } else {
                storage = loadLiveStorage(address);

                Files.createDirectories(Paths.get(STORAGE_DIR));
                mapper.writeValue(storagePath.toFile(), storage);
            }

            return storage;
        }

        @Bean
        public Storage storage() throws IOException {
            Map<DataWord, DataWord> storage = loadStorage(Config.DAO_ADDRESS);

            return new Storage() {
                @Override
                public int size(byte[] address) {
                    return storage.size();
                }

                @Override
                public Map<DataWord, DataWord> entries(byte[] address, List<DataWord> keys) {
                    return storage.entrySet().stream()
                            .filter(entry -> keys.contains(entry.getKey()))
                            .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
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

        @Bean
        public LevelDbDataSource storageDict() {
            LevelDbDataSource dataSource = new LevelDbDataSource("storageDict");
            dataSource.init();
            return dataSource;
        }
    }


    @Autowired
    private ContractDataService contractDataService;

    private String contractSource;

    @Value("${classpath:contracts/real/DAO.sol}")
    public void setContractSource(Resource contractSource) throws IOException {
        this.contractSource = resourceToString(contractSource);
    }


    @Test
    public void test() throws IOException {
        Ast.Contract contractAst = getContractAllDataMembers(contractSource, "DAO");
        Instant start = now();
        com.ethercamp.contrdata.storage.StoragePage page = contractDataService.getContractData(Config.DAO_ADDRESS, contractAst.toJson(), Path.of(0), 0, 100);
        System.out.println("getting contract data takes: " + between(start, now()).toMillis() + " ms");

        System.out.println(new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValueAsString(page));
    }
}
