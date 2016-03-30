package com.ethercamp.contrdata;

import com.ethercamp.contrdata.config.ContractDataConfig;
import com.ethercamp.contrdata.contract.Ast;
import com.ethercamp.contrdata.contract.ContractData;
import com.ethercamp.contrdata.storage.Path;
import com.ethercamp.contrdata.storage.Storage;
import com.ethercamp.contrdata.storage.StorageEntry;
import com.ethercamp.contrdata.storage.StoragePage;
import com.ethercamp.contrdata.storage.dictionary.StorageDictionary;
import com.ethercamp.contrdata.storage.dictionary.StorageDictionaryDb;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.datasource.LevelDbDataSource;
import org.ethereum.vm.DataWord;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.toMap;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

@ContextConfiguration(loader = AnnotationConfigContextLoader.class, classes = {
        ContractDataConfig.class, BaseTest.Config.class, StorageDictTest.Config.class
})
public class StorageDictTest extends BaseTest{

    @Configuration
    static class Config {

        @Bean
        public Storage storage() {
            Map<DataWord, DataWord> storage = storageMap();
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

        private Map<DataWord, DataWord> storageMap() {
            try {
                String asJson = "{\"0000000000000000000000000000000000000000000000000000000000000004\":\"000000000000000000000000b8c492d47ab446701786cc6d0c85e746b6e41885\",\"0000000000000000000000000000000000000000000000000000000000000000\":\"00000000000000000000000020e12a1f859b3feae5fb2a0a32c18f5a65555bbf\"}\n";
                 return new ObjectMapper().readValue(asJson, new TypeReference<Map<DataWord, DataWord>>() {
                });
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Bean
        public KeyValueDataSource storageDict() {
            LevelDbDataSource dataSource = new LevelDbDataSource("storageDict");
            dataSource.init();
            return dataSource;
        }
    }

    @Autowired
    private StorageDictionaryDb dictionaryDb;
    @Autowired
    private ContractDataService contractDataService;
    @Value("${classpath:contracts/real/YoutubeViews.sol}")
    private Resource youtubeViewsSource;

    @Test
    public void test() throws IOException {
        byte[] address = Hex.decode("956a285faa86b212ec51ad9da0ede6c8861e3a33");

        StorageDictionary dictionary = dictionaryDb.getOrCreate(StorageDictionaryDb.Layout.Solidity, address);
//        System.out.println(contractDataService.dumpDict(dictionary));


        Ast.Contract dataMembers = getContractAllDataMembers(youtubeViewsSource, "YoutubeViews");

        StoragePage storagePage = contractDataService.getContractData(address, new ContractData(dataMembers, dictionary), Path.empty(), 0, 20);
        List<StorageEntry> entries = storagePage.getEntries();

        assertNotNull(entries);
        assertFalse(entries.isEmpty());

        System.out.println(new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValueAsString(entries));
    }
}
