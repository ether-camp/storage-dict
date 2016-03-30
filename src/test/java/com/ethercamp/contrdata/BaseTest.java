package com.ethercamp.contrdata;

import com.ethercamp.contrdata.bc.LocalBlockchain;
import com.ethercamp.contrdata.bc.StandaloneBlockchain;
import com.ethercamp.contrdata.config.ContractDataConfig;
import com.ethercamp.contrdata.contract.Ast;
import com.ethercamp.contrdata.contract.ContractData;
import com.ethercamp.contrdata.storage.Storage;
import com.ethercamp.contrdata.storage.dictionary.StorageDictionaryDb;
import org.ethereum.config.SystemProperties;
import org.ethereum.config.blockchain.FrontierConfig;
import org.ethereum.config.net.MainNetConfig;
import org.ethereum.core.Repository;
import org.ethereum.datasource.HashMapDB;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.solidity.compiler.SolidityCompiler;
import org.ethereum.vm.DataWord;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.junit.Assert.*;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(loader = AnnotationConfigContextLoader.class, classes = {
        ContractDataConfig.class, BaseTest.Config.class
})
public abstract class BaseTest {

    @Configuration
    static class Config {

        @Bean
        public LocalBlockchain localBlockchain() {
            return new StandaloneBlockchain();
        }

        @Bean
        public Storage storage() {
            Repository repository = localBlockchain().getBlockchainRepository();
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

        @Bean
        public KeyValueDataSource storageDict() {
            return new HashMapDB();
        }
    }


    @Autowired
    protected LocalBlockchain blockchain;
    @Autowired
    protected StorageDictionaryDb dictDb;

    @BeforeClass
    public static void setup() {
        SystemProperties.CONFIG.setBlockchainConfig(new FrontierConfig(new FrontierConfig.FrontierConstants() {
            @Override
            public BigInteger getMINIMUM_DIFFICULTY() {
                return BigInteger.ONE;
            }
        }));
    }

    @AfterClass
    public static void cleanup() {
        SystemProperties.CONFIG.setBlockchainConfig(MainNetConfig.INSTANCE);
    }

    protected static String resourceToString(Resource resource) throws IOException {
        return new String(resourceToBytes(resource));
    }

    private static byte[] resourceToBytes(Resource resource) throws IOException {
        return Files.readAllBytes(Paths.get(resource.getURI()));
    }

    private static Ast.Contract getContractAllDataMembers(byte[] bytes, String contractName) throws IOException {
        SolidityCompiler.Result result = SolidityCompiler.compile(bytes, false, SolidityCompiler.Options.AST);
        assertFalse(result.errors, result.isFailed());
        return Ast.parse(result.output).getContractAllDataMembers(contractName);
    }

    protected static Ast.Contract getContractAllDataMembers(String sol, String contractName) throws IOException {
        return getContractAllDataMembers(sol.getBytes(), contractName);
    }

    protected static Ast.Contract getContractAllDataMembers(Resource sol, String contractName) throws IOException {
        return getContractAllDataMembers(resourceToBytes(sol), contractName);
    }

    protected static void assertStructEqual(ContractData.Element structEl, Function<DataWord, DataWord> valueExtractor, String... values) {
        assertTrue(structEl.getType().isStruct());
        assertEquals(values.length, structEl.getChildrenCount());

        List<ContractData.Element> fields = structEl.getChildren(0, structEl.getChildrenCount());
        assertEquals(structEl.getChildrenCount(), fields.size());

        for (int i = 0; i < values.length; i++) {
            assertEquals(values[i], fields.get(i).getValue(valueExtractor));
        }
    }

    protected static void assertFieldEqual(ContractData.Element field, Function<DataWord, DataWord> valueExtractor, String type, String name, String value) {
        assertNotNull(field);
        assertEquals(name, field.getName());
        assertEquals(type, field.getType().getName());
        assertEquals(value, field.getValue(valueExtractor));
    }
}
