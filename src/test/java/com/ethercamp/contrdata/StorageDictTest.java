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
import org.apache.commons.collections4.keyvalue.DefaultKeyValue;
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
import java.util.*;

import static java.util.stream.Collectors.toMap;
import static org.ethereum.util.ByteUtil.toHexString;
import static org.junit.Assert.*;

@ContextConfiguration(loader = AnnotationConfigContextLoader.class, classes = {
        ContractDataConfig.class, BaseTest.Config.class, StorageDictTest.Config.class
})
public class StorageDictTest extends BaseTest{

    @Configuration
    static class Config {

        @Bean
        public Storage storage() {
            return new Storage() {
                @Override
                public int size(byte[] address) {
                    return storageMap(address).size();
                }

                @Override
                public Map<DataWord, DataWord> entries(byte[] address, List<DataWord> keys) {
                    return storageMap(address).entrySet().stream()
                            .filter(entry -> keys.contains(entry.getKey()))
                            .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
                }

                @Override
                public Set<DataWord> keys(byte[] address) {
                    return storageMap(address).keySet();
                }

                @Override
                public DataWord get(byte[] address, DataWord key) {
                    return storageMap(address).get(key);
                }
            };
        }

        private static Map<String, String> storageByAddress = new HashMap<String, String>() {{
            put("956a285faa86b212ec51ad9da0ede6c8861e3a33", "{\"0000000000000000000000000000000000000000000000000000000000000004\":\"000000000000000000000000b8c492d47ab446701786cc6d0c85e746b6e41885\",\"0000000000000000000000000000000000000000000000000000000000000000\":\"00000000000000000000000020e12a1f859b3feae5fb2a0a32c18f5a65555bbf\"}\n");
            put("a45d606fd52659ef161a45e21fff060b950612f7", "{\"0000000000000000000000000000000000000000000000000000000000000000\":\"000000000000000000000002640eb8074d09975f11d756985e4fe0863e52c393\"}\n");
            put("7d96e318ac2a5048a2f901e65a5c1d610cfb8094", "{\"405d1087a265de75abc55579557f00cdbab73e5ae3953c584a395dab344ecd1a\":\"3078303437303335336161643136613237353830633437386561303763313933\",\"8a35acfbc15ff81a39ae7d344fd709f28e8600b4aa8c65c6b64bfe7fe36bd19b\":\"0000000000000000000043f41cdca2f6785642928bcd2265fe9aff02911a0000\",\"0000000000000000000000000000000000000000000000000000000000000004\":\"0000000000000000000000000000000000000000000000000000000000000001\",\"9225dd3fe13c7b6be982616bd0c309a57f51f065763c845653f6af7ce26fde6e\":\"0000000000000000000000000000000000000000000000000000000000000001\",\"0000000000000000000000000000000000000000000000000000000000000003\":\"6100000000000000000000000000000000000000000000000000000000000000\",\"0000000000000000000000000000000000000000000000000000000000000002\":\"00000000000000000043f41cdca2f6785642928bcd2265fe9aff02911a000106\",\"0000000000000000000000000000000000000000000000000000000000000001\":\"000000000000000002c68af0bb14000000000000000000004563918244f40000\",\"0000000000000000000000000000000000000000000000000000000000000000\":\"6100000000000000000000000000000000000000000000000000000000000000\",\"8a35acfbc15ff81a39ae7d344fd709f28e8600b4aa8c65c6b64bfe7fe36bd19c\":\"0000000000000000000000000000000000000000000000000000000000000109\",\"8a35acfbc15ff81a39ae7d344fd709f28e8600b4aa8c65c6b64bfe7fe36bd19d\":\"676f6f0000000000000000000000000000000000000000000000000000000000\",\"405d1087a265de75abc55579557f00cdbab73e5ae3953c584a395dab344ecd1d\":\"6339303665353336306536386164376333303039353835643365623538613265\",\"405d1087a265de75abc55579557f00cdbab73e5ae3953c584a395dab344ecd1e\":\"3430326200000000000000000000000000000000000000000000000000000000\",\"405d1087a265de75abc55579557f00cdbab73e5ae3953c584a395dab344ecd1b\":\"3531386531353162653934316439646333333032356366343865393439633261\",\"405d1087a265de75abc55579557f00cdbab73e5ae3953c584a395dab344ecd1c\":\"6136366465313235383339643538343164643064643364363737666535353230\"}\n");
            put("ab7648c7664da59badeb9fa321b8111e6f29bc3e", "{\"405787fa12a823e0f2b7631cc41b3ba8828b3321ca811111fa75cd3aa3bb5ad0\":\"67656f726779000000000000000000000000000000000000000000000000000c\",\"405787fa12a823e0f2b7631cc41b3ba8828b3321ca811111fa75cd3aa3bb5ad1\":\"657567656e65000000000000000000000000000000000000000000000000000c\",\"405787fa12a823e0f2b7631cc41b3ba8828b3321ca811111fa75cd3aa3bb5ad2\":\"76616c656e74696e000000000000000000000000000000000000000000000010\",\"405787fa12a823e0f2b7631cc41b3ba8828b3321ca811111fa75cd3aa3bb5ad3\":\"000000000000000000000000000000000000000000000000000000000000006d\",\"0000000000000000000000000000000000000000000000000000000000000002\":\"0000000000000000000000000000000000000000000000000000000000000003\",\"0000000000000000000000000000000000000000000000000000000000000001\":\"7068696c6970000000000000000000000000000000000000000000000000000c\",\"0000000000000000000000000000000000000000000000000000000000000000\":\"616e61746f6c790000000000000000000000000000000000000000000000000e\",\"405787fa12a823e0f2b7631cc41b3ba8828b3321ca811111fa75cd3aa3bb5ace\":\"67656f726779000000000000000000000000000000000000000000000000000c\",\"405787fa12a823e0f2b7631cc41b3ba8828b3321ca811111fa75cd3aa3bb5acf\":\"646d69747279000000000000000000000000000000000000000000000000000c\",\"e5126a4d711f2dd98aa7df46b100c291503dddb43ad8180ae07f600704524a9d\":\"6c6f6f6f6f6f6f6f6f6f6f6f6f6f6f6f6f6e67206c6f6f6f6f6f6f6f6f6f6f6f\",\"e5126a4d711f2dd98aa7df46b100c291503dddb43ad8180ae07f600704524a9e\":\"6f6f6f6f6f6f6f6f6f6e67206368696c64206e616d6500000000000000000000\"}\n");
        }};

        private Map<DataWord, DataWord> storageMap(byte[] address) {
            try {
                String storage = storageByAddress.get(toHexString(address));
                return new ObjectMapper().readValue(storage, new TypeReference<Map<DataWord, DataWord>>() {
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
    public void youtubeViewsTest() throws IOException {
        byte[] address = Hex.decode("956a285faa86b212ec51ad9da0ede6c8861e3a33");

        StorageDictionary dictionary = dictionaryDb.getOrCreate(StorageDictionaryDb.Layout.Solidity, address);
        Ast.Contract dataMembers = getContractAllDataMembers(youtubeViewsSource, "YoutubeViews");

        StoragePage storagePage = contractDataService.getContractData(address, new ContractData(dataMembers, dictionary), Path.empty(), 0, 20);
        List<StorageEntry> entries = storagePage.getEntries();

        assertNotNull(entries);
        assertFalse(entries.isEmpty());

        System.out.println(new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValueAsString(entries));
    }

    @Value("${classpath:contracts/real/ProjectKudos.sol}")
    private Resource projectKudosSource;

    @Test
    public void projectKudosTest() throws IOException {
        byte[] address = Hex.decode("a45d606fd52659ef161a45e21fff060b950612f7");

        StorageDictionary dictionary = dictionaryDb.getOrCreate(StorageDictionaryDb.Layout.Solidity, address);
        Ast.Contract dataMembers = getContractAllDataMembers(projectKudosSource, "ProjectKudos");

        StoragePage storagePage = contractDataService.getContractData(address, new ContractData(dataMembers, dictionary), Path.empty(), 0, 20);
        List<StorageEntry> entries = storagePage.getEntries();

        assertNotNull(entries);
        assertFalse(entries.isEmpty());

        System.out.println(new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValueAsString(entries));
    }

    @Value("${classpath:contracts/real/EtherPokerTable.sol}")
    private Resource etherPokerTableSource;

    @Test
    public void etherPokerTableTest() throws IOException {
        byte[] address = Hex.decode("7d96e318ac2a5048a2f901e65a5c1d610cfb8094");

        StorageDictionary dictionary = dictionaryDb.getOrCreate(StorageDictionaryDb.Layout.Solidity, address);
        Ast.Contract dataMembers = getContractAllDataMembers(etherPokerTableSource, "EtherPokerTable");

        StoragePage storagePage = contractDataService.getContractData(address, new ContractData(dataMembers, dictionary), Path.of(8, 0), 0, 20);
        List<StorageEntry> entries = storagePage.getEntries();

        assertNotNull(entries);
        assertEquals(4, entries.size());
        assertEquals(4, storagePage.getTotal());

        System.out.println(new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValueAsString(storagePage));
    }

    @Value("${classpath:contracts/struct/NestedStruct.sol}")
    private Resource nestedStructeSource;

    @Test
    public void nestedStructTest() throws IOException {
        byte[] address = Hex.decode("ab7648c7664da59badeb9fa321b8111e6f29bc3e");

        StorageDictionary dictionary = dictionaryDb.getOrCreate(StorageDictionaryDb.Layout.Solidity, address);
        Ast.Contract dataMembers = getContractAllDataMembers(nestedStructeSource, "NestedStruct");

        StoragePage storagePage = contractDataService.getContractData(address, new ContractData(dataMembers, dictionary), Path.of(1,1,1), 0, 20);
        List<StorageEntry> entries = storagePage.getEntries();

        assertNotNull(entries);

        System.out.println(new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValueAsString(storagePage));
    }

    private static class StorageTranslator extends ArrayList<StorageTranslator.Entry> {

        private static final ObjectMapper MAPPER = new ObjectMapper();

        static class Entry extends DefaultKeyValue<DataWord, DataWord>{
            public String type;
        }

        public static String translate(String input) throws IOException {
            Map<DataWord, DataWord> storageMap = MAPPER.readValue(input, StorageTranslator.class).stream().collect(toMap(Entry::getKey, Entry::getValue));
            return MAPPER.writeValueAsString(storageMap);
        }

        public static void main(String[] args) throws IOException {
            String input = "";
            System.out.println(translate(input));
        }
    }
}
