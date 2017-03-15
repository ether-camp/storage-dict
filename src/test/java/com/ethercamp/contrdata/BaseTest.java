package com.ethercamp.contrdata;

import com.ethercamp.contrdata.config.ContractDataConfig;
import com.ethercamp.contrdata.contract.Ast;
import com.ethercamp.contrdata.contract.ContractData;
import com.ethercamp.contrdata.storage.Path;
import com.ethercamp.contrdata.storage.Storage;
import com.ethercamp.contrdata.storage.dictionary.Layout;
import com.ethercamp.contrdata.storage.dictionary.StorageDictionary;
import com.ethercamp.contrdata.storage.dictionary.StorageDictionaryDb;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.SneakyThrows;
import org.ethereum.config.SystemProperties;
import org.ethereum.config.blockchain.FrontierConfig;
import org.ethereum.config.net.MainNetConfig;
import org.ethereum.core.BlockchainImpl;
import org.ethereum.core.Repository;
import org.ethereum.datasource.DbSource;
import org.ethereum.datasource.inmem.HashMapDB;
import org.ethereum.db.ContractDetails;
import org.ethereum.solidity.compiler.SolidityCompiler;
import org.ethereum.util.blockchain.SolidityContract;
import org.ethereum.util.blockchain.StandaloneBlockchain;
import org.ethereum.vm.DataWord;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;

import static com.ethercamp.contrdata.storage.Path.parseHumanReadable;
import static java.util.Collections.emptySet;
import static java.util.Objects.isNull;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.junit.Assert.*;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(loader = AnnotationConfigContextLoader.class, classes = {
        ContractDataConfig.class, BaseTest.Config.class
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
public abstract class BaseTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    @Configuration
    static class Config {

        @Bean
        public StandaloneBlockchain localBlockchain() {
            return new StandaloneBlockchain()
                    .withAutoblock(true)
                    .withGasLimit(3_000_000_000L);
        }

        @Bean
        public Storage storage() {
            Repository repository = localBlockchain().getBlockchain().getRepository();
            return new HashMapStorage(repository, storageDictionaryDb());
        }

        @Bean
        public DbSource<byte[]> storageDict() {
            return new HashMapDBExt();
        }

        @Bean
        public StorageDictionaryDb storageDictionaryDb() {
            return new StorageDictionaryDb(storageDict());
        }
    }

    public static class HashMapDBExt extends HashMapDB {
        public Map source() {
            return storage;
        }
    }


    @Autowired
    protected StandaloneBlockchain blockchain;
    @Autowired
    protected Storage storage;
    @Autowired
    protected StorageDictionaryDb dictDb;

    @BeforeClass
    public static void setup() {
        SystemProperties.getDefault().setBlockchainConfig(new FrontierConfig(new FrontierConfig.FrontierConstants() {
            @Override
            public BigInteger getMINIMUM_DIFFICULTY() {
                return BigInteger.ONE;
            }
        }));
    }

    @AfterClass
    public static void cleanup() {
        SystemProperties.getDefault().setBlockchainConfig(MainNetConfig.INSTANCE);
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

    protected Function<DataWord, DataWord> newValueExtractor(SolidityContract contract) {
        return key -> storage.get(contract.getAddress(), key);
    }

    protected static void assertStructEqual(ContractData.Element structEl, Function<DataWord, DataWord> valueExtractor, Object... values) {
        assertTrue(structEl.getType().isStruct());
        assertEquals(values.length, structEl.getChildrenCount());

        List<ContractData.Element> fields = structEl.getChildren(0, structEl.getChildrenCount());
        assertEquals(structEl.getChildrenCount(), fields.size());

        for (int i = 0; i < values.length; i++) {
            assertEquals(Objects.toString(values[i]), fields.get(i).getValue(valueExtractor));
        }
    }

    protected static void assertFieldEqual(ContractData.Element field, Function<DataWord, DataWord> valueExtractor, String type, String name, String value) {
        assertNotNull(field);
        assertEquals(name, field.getKey());
        assertEquals(type, field.getType().getName());
        assertEquals(value, field.getValue(valueExtractor));
    }

    @SneakyThrows
    protected static String toJson(Object object) {
        return OBJECT_MAPPER.writeValueAsString(object);
    }

    protected void printStorageInfo(SolidityContract contract) {
        ContractDetails details = ((BlockchainImpl) blockchain.getBlockchain()).getRepository().getContractDetails(contract.getAddress());

        Set<DataWord> keys = storage.keys(contract.getAddress());
        Map<DataWord, DataWord> entries = storage.entries(contract.getAddress(), new ArrayList<>(keys));
        System.out.printf("Storage:\n%s\n", toJson(entries));

        StorageDictionary dictionary = dictDb.getDictionaryFor(Layout.Lang.solidity, contract.getAddress());
        StorageDictionary.PathElement root = dictionary.getByPath();
        System.out.printf("Storage dictionary:\n%s\n", root.toString(details, 2));
    }

    protected ContractData getContractData(SolidityContract contract, String source, String contractName) throws IOException {
        Ast.Contract ast = getContractAllDataMembers(source, contractName);
        StorageDictionary dictionary = dictDb.getDictionaryFor(Layout.Lang.solidity, contract.getAddress());

        return new ContractData(ast, dictionary);
    }

    protected static ContractData.Element getElement(ContractData contractData, String humanReadablePath, Object... pathArgs) {
        Path path = parseHumanReadable(String.format(humanReadablePath, pathArgs), contractData);
        return contractData.elementByPath(path.parts());
    }

    public static class HashMapStorage implements Storage {

        private final Repository repository;
        private final StorageDictionaryDb storageDictionaryDb;

        public HashMapStorage(Repository repository, StorageDictionaryDb storageDictionaryDb) {
            this.repository = repository;
            this.storageDictionaryDb = storageDictionaryDb;
        }

        @Override
        public int size(byte[] address) {
            return keys(address).size();
        }

        @Override
        public Map<DataWord, DataWord> entries(byte[] address, List<DataWord> keys) {
            ContractDetails contractDetails = repository.getContractDetails(address);
            return keys.stream().collect(toMap(identity(), k -> contractDetails.get(k)));
        }

        @Override
        public Set<DataWord> keys(byte[] address) {
            StorageDictionary storageDictionary = storageDictionaryDb.getDictionaryFor(Layout.Lang.solidity, address);
            return isNull(storageDictionary)
                    ? emptySet()
                    : findKeysIn(address, storageDictionary.getByPath(), new HashSet<>());
        }

        // stack overflow may occur
        private Set<DataWord> findKeysIn(byte[] address, StorageDictionary.PathElement rootElement, HashSet<DataWord> dataWords) {
            rootElement.getChildren().forEach(element -> {
                if (element.hasChildren()) {
                    findKeysIn(address, element, dataWords);
                } else {
                    DataWord key = new DataWord(element.storageKey);
                    if (repository.getStorageValue(address, key) != null) {
                        dataWords.add(key);
                    }
                }
            });
            return dataWords;
        }


        @Override
        public DataWord get(byte[] address, DataWord key) {
            return repository.getContractDetails(address).get(key);
        }
    }
}
