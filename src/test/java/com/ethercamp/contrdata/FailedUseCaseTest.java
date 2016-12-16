package com.ethercamp.contrdata;

import com.ethercamp.contrdata.config.ContractDataConfig;
import com.ethercamp.contrdata.contract.ContractData;
import com.ethercamp.contrdata.storage.Storage;
import com.ethercamp.contrdata.storage.dictionary.StorageDictionary;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ethereum.datasource.DbSource;
import org.ethereum.datasource.inmem.HashMapDB;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.lang.String.format;
import static org.ethereum.util.ByteUtil.toHexString;
import static org.junit.Assert.fail;

@ContextConfiguration(loader = AnnotationConfigContextLoader.class, classes = {
        ContractDataConfig.class, BaseTest.Config.class, FailedUseCaseTest.Config.class
})
public class FailedUseCaseTest extends BaseTest {

    @Configuration
    static class Config  {

        private Map<String, Map<DataWord, DataWord>> storages = new HashMap<>();

        @Value("${classpath:contracts/failed/CSGOBets}")
        public void addCSGOBets(Resource useCase) {
            parseUseCase(useCase);
        }

        @Value("${classpath:contracts/failed/EtherAuction}")
        public void addEtherAuction(Resource useCase) {
            parseUseCase(useCase);
        }

        @Value("${classpath:contracts/failed/EthereumDice}")
        public void addEthereumDice(Resource useCase) {
            parseUseCase(useCase);
        }

        @Value("${classpath:contracts/failed/etherlist_top}")
        public void addEtherListTop(Resource useCase) {
            parseUseCase(useCase);
        }

        @Value("${classpath:contracts/failed/BetOnHashV81}")
        public void addBetOnHashV81(Resource useCase) {
            parseUseCase(useCase);
        }

        public void parseUseCase(Resource useCase) {
            try {
                FailedUseCase failedUseCase = new ObjectMapper().readValue(resourceToString(useCase), FailedUseCase.class);
                useCases().add(failedUseCase);

                storages.put(failedUseCase.getAddress(), failedUseCase.getStorage());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Bean
        public FailedUseCase.List useCases() {
            return new FailedUseCase.List();
        }

        @Bean
        public Storage storage() {
            return new Storage() {
                @Override
                public int size(byte[] address) {
                    return storages.get(toHexString(address)).size();
                }

                @Override
                public Map<DataWord, DataWord> entries(byte[] address, List<DataWord> keys) {
                    return storages.get(toHexString(address));
                }

                @Override
                public Set<DataWord> keys(byte[] address) {
                    return storages.get(toHexString(address)).keySet();
                }

                @Override
                public DataWord get(byte[] address, DataWord key) {
                    return storages.get(toHexString(address)).get(key);
                }
            };
        }

        @Bean
        public DbSource storageDict() {
            return new HashMapDB();
        }
    }


    @Autowired
    private FailedUseCase.List useCases;
    @Autowired
    private ContractDataService contractDataService;

    @Test
    public void test() throws IOException {
        for (FailedUseCase useCase : useCases) {
            byte[] address = Hex.decode(useCase.getAddress());
            StorageDictionary dictionary = StorageDictionary.readDmp(useCase.getStorageDictionary());
            ContractData contractData = new ContractData(useCase.getDataMembers(), dictionary);

            try {
                contractDataService.getContractData(address, contractData, false, useCase.getPath(), 0, 1000);
            } catch (Exception e) {
                fail(format("Cannot build contract data model for '%s'", contractData.getContract().getName()));
            }

        }
    }
}
