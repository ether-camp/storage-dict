package com.ethercamp.contrdata;

import com.ethercamp.contrdata.config.ContractDataConfig;
import com.ethercamp.contrdata.contract.Ast;
import com.ethercamp.contrdata.contract.ContractData;
import com.ethercamp.contrdata.storage.Path;
import com.ethercamp.contrdata.storage.Storage;
import com.ethercamp.contrdata.storage.StorageEntry;
import com.ethercamp.contrdata.storage.StoragePage;
import com.ethercamp.contrdata.storage.dictionary.Layout;
import com.ethercamp.contrdata.storage.dictionary.StorageDictionary;
import com.ethercamp.contrdata.storage.dictionary.StorageDictionaryDb;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.commons.collections4.keyvalue.DefaultKeyValue;
import org.ethereum.datasource.DbSource;
import org.ethereum.datasource.leveldb.LevelDbDataSource;
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
            put("bc4a3057325dfdde568f66ab70548df12d53aa85", "{\"e1b2d8011290065d3fd6fe177548f21f2f780847d689783670f30c6738af1a26\":\"0000000000000000000000000000000000000000000000000000000000000001\",\"975c31cc06079301c59504e7a5bdd4edca6ae150976db8ed3670c8a8c6701c5e\":\"0000000000000000000000000000000000000000000000000000000000000002\",\"6c9ebdd66b497ac81db5a3cebe1c842b4855f459630be3f1dbbb6ed69dfd0243\":\"0000000000000000000000000000000000000000000000000000000000000064\",\"e1b2d8011290065d3fd6fe177548f21f2f780847d689783670f30c6738af1a24\":\"000000000000000000000000000000000000000000000000000000000000022b\",\"c6b835561011369b2967afedb50ba32e8d2f37a254bbae5696f5cd283737ae61\":\"000000000000000000000000664abf405c34ccb6dccf5a4cabf14efbd92671c4\",\"6c9ebdd66b497ac81db5a3cebe1c842b4855f459630be3f1dbbb6ed69dfd0244\":\"000000000000000000000000000000000000000000000000000000000000000d\",\"c6b835561011369b2967afedb50ba32e8d2f37a254bbae5696f5cd283737ae62\":\"0000000000000000000000001411a9ff38d0d06b1220c183527d25023b01e833\",\"6c9ebdd66b497ac81db5a3cebe1c842b4855f459630be3f1dbbb6ed69dfd0245\":\"0000000000000000000000000000000000000000000000000000000000000001\",\"63993338fa5a1f227a084036717e8c8da11b8d3ea59ae69d9b0cf54caad59fa4\":\"0000000000000000000000000000000000000000000000000000000000000001\",\"0000000000000000000000000000000000000000000000000000000000000000\":\"000000000000000000000000640eb8074d09975f11d756985e4fe0863e52c393\",\"53633e7186b5aca0f490e7fe406607c7e3e27fbc20f762f80fe2b47aa9822d3a\":\"000000000000000000000000000000000000000000000000000000000000000a\",\"53633e7186b5aca0f490e7fe406607c7e3e27fbc20f762f80fe2b47aa9822d39\":\"0000000000000000000000000000000000000000000000000000000000000003\",\"ac33ff75c19e70fe83507db0d683fd3465c996598dc972688b7ace676c89077b\":\"00000000000000000000000000000000000000000000000000000000000000c8\",\"80704e1f616e7ec65be637df77518814ba6b9e3f3d8adc07d33626b38164be00\":\"0000000000000000000000000000000000000000000000000000000000000001\",\"f75a9a1b4a7be0961fc4cfce12d61f5a4d14d36046a217c9f1a05817ca7958ff\":\"0000000000000000000000000000000000000000000000000000000000000001\",\"07e02ed9a8a38f3c8bc970c6c3e96ff28c8eef46a3f7a016d7c9b6a81ba086de\":\"0000000000000000000000000000000000000000000000000000000000000378\",\"5ce6abc3973d26bca95c54bbe770c9a349fb5136300dff59d374dcb98f65c6ff\":\"0000000000000000000000000000000000000000000000000000000000000003\",\"b7da4bde56772dd6ab14fdedd926631a179db641c4d55542f88f7c069d2884ad\":\"00000000000000000000000000000000000000000000000000000000000004dd\",\"b7da4bde56772dd6ab14fdedd926631a179db641c4d55542f88f7c069d2884af\":\"0000000000000000000000000000000000000000000000000000000000000001\",\"78779b8c540aea86d686aad3e83b94b481975378f69dac9b40a902b3f7b4fe36\":\"0000000000000000000000000000000000000000000000000000000000000002\",\"63993338fa5a1f227a084036717e8c8da11b8d3ea59ae69d9b0cf54caad59fa2\":\"0000000000000000000000000000000000000000000000000000000000000309\",\"78779b8c540aea86d686aad3e83b94b481975378f69dac9b40a902b3f7b4fe37\":\"0000000000000000000000000000000000000000000000000000000000000002\",\"825a2d1269511b5f44bd017fd324f1009ba606d3bbbc60f076f89db601ff1d11\":\"000000000000000000000000000000000000000000000000000000000000000a\",\"bc3eb6fb906f29da0efe42f23a915dea703c52cd04d85949ebaf0223fbbc81b1\":\"0000000000000000000000000000000000000000000000000000000000000003\",\"ceea2ca640be51ab56d7292c4c2c186f9cacb28fdb37d7fe1a97dff4b12008c8\":\"0000000000000000000000000000000000000000000000000000000000000001\",\"ceea2ca640be51ab56d7292c4c2c186f9cacb28fdb37d7fe1a97dff4b12008c6\":\"000000000000000000000000000000000000000000000000000000000000014d\",\"07e02ed9a8a38f3c8bc970c6c3e96ff28c8eef46a3f7a016d7c9b6a81ba086e0\":\"0000000000000000000000000000000000000000000000000000000000000001\",\"c5e2b3367b86f14cfb33d131aab5a8be73a43f38753d50f2bb51650a13cfa5f8\":\"000000000000000000000000000000000000000000000000000000000000000a\",\"80704e1f616e7ec65be637df77518814ba6b9e3f3d8adc07d33626b38164bdfe\":\"000000000000000000000000000000000000000000000000000000000000007b\"}\n");
            put("7d96e318ac2a5048a2f901e65a5c1d610cfb8094", "{\"405d1087a265de75abc55579557f00cdbab73e5ae3953c584a395dab344ecd1a\":\"3078303437303335336161643136613237353830633437386561303763313933\",\"8a35acfbc15ff81a39ae7d344fd709f28e8600b4aa8c65c6b64bfe7fe36bd19b\":\"0000000000000000000043f41cdca2f6785642928bcd2265fe9aff02911a0000\",\"0000000000000000000000000000000000000000000000000000000000000004\":\"0000000000000000000000000000000000000000000000000000000000000001\",\"9225dd3fe13c7b6be982616bd0c309a57f51f065763c845653f6af7ce26fde6e\":\"0000000000000000000000000000000000000000000000000000000000000001\",\"0000000000000000000000000000000000000000000000000000000000000003\":\"6100000000000000000000000000000000000000000000000000000000000000\",\"0000000000000000000000000000000000000000000000000000000000000002\":\"00000000000000000043f41cdca2f6785642928bcd2265fe9aff02911a000106\",\"0000000000000000000000000000000000000000000000000000000000000001\":\"000000000000000002c68af0bb14000000000000000000004563918244f40000\",\"0000000000000000000000000000000000000000000000000000000000000000\":\"6100000000000000000000000000000000000000000000000000000000000000\",\"8a35acfbc15ff81a39ae7d344fd709f28e8600b4aa8c65c6b64bfe7fe36bd19c\":\"0000000000000000000000000000000000000000000000000000000000000109\",\"8a35acfbc15ff81a39ae7d344fd709f28e8600b4aa8c65c6b64bfe7fe36bd19d\":\"676f6f0000000000000000000000000000000000000000000000000000000000\",\"405d1087a265de75abc55579557f00cdbab73e5ae3953c584a395dab344ecd1d\":\"6339303665353336306536386164376333303039353835643365623538613265\",\"405d1087a265de75abc55579557f00cdbab73e5ae3953c584a395dab344ecd1e\":\"3430326200000000000000000000000000000000000000000000000000000000\",\"405d1087a265de75abc55579557f00cdbab73e5ae3953c584a395dab344ecd1b\":\"3531386531353162653934316439646333333032356366343865393439633261\",\"405d1087a265de75abc55579557f00cdbab73e5ae3953c584a395dab344ecd1c\":\"6136366465313235383339643538343164643064643364363737666535353230\"}\n");
            put("ab7648c7664da59badeb9fa321b8111e6f29bc3e", "{\"405787fa12a823e0f2b7631cc41b3ba8828b3321ca811111fa75cd3aa3bb5ad0\":\"67656f726779000000000000000000000000000000000000000000000000000c\",\"405787fa12a823e0f2b7631cc41b3ba8828b3321ca811111fa75cd3aa3bb5ad1\":\"657567656e65000000000000000000000000000000000000000000000000000c\",\"405787fa12a823e0f2b7631cc41b3ba8828b3321ca811111fa75cd3aa3bb5ad2\":\"76616c656e74696e000000000000000000000000000000000000000000000010\",\"405787fa12a823e0f2b7631cc41b3ba8828b3321ca811111fa75cd3aa3bb5ad3\":\"000000000000000000000000000000000000000000000000000000000000006d\",\"0000000000000000000000000000000000000000000000000000000000000002\":\"0000000000000000000000000000000000000000000000000000000000000003\",\"0000000000000000000000000000000000000000000000000000000000000001\":\"7068696c6970000000000000000000000000000000000000000000000000000c\",\"0000000000000000000000000000000000000000000000000000000000000000\":\"616e61746f6c790000000000000000000000000000000000000000000000000e\",\"405787fa12a823e0f2b7631cc41b3ba8828b3321ca811111fa75cd3aa3bb5ace\":\"67656f726779000000000000000000000000000000000000000000000000000c\",\"405787fa12a823e0f2b7631cc41b3ba8828b3321ca811111fa75cd3aa3bb5acf\":\"646d69747279000000000000000000000000000000000000000000000000000c\",\"e5126a4d711f2dd98aa7df46b100c291503dddb43ad8180ae07f600704524a9d\":\"6c6f6f6f6f6f6f6f6f6f6f6f6f6f6f6f6f6e67206c6f6f6f6f6f6f6f6f6f6f6f\",\"e5126a4d711f2dd98aa7df46b100c291503dddb43ad8180ae07f600704524a9e\":\"6f6f6f6f6f6f6f6f6f6e67206368696c64206e616d6500000000000000000000\"}\n");
            put("85a6ef0ae351abffb1200aa605cb7e3058072ae3", "{\"0000000000000000000000000000000000000000000000000000000000000005\":\"0000000000000000000000000000000000000000000000000000000000000001\",\"0000000000000000000000000000000000000000000000000000000000000004\":\"0000000000000000000000000000000000000000000000000000000000000084\",\"0000000000000000000000000000000000000000000000000000000000000003\":\"0000000000000000000000000000000000000000000000000000000000000001\",\"0000000000000000000000000000000000000000000000000000000000000002\":\"726f6f74206c6576656c20737472756374000000000000000000000000000022\",\"0000000000000000000000000000000000000000000000000000000000000001\":\"0000000000000000000000000000000000000000000000000000000000000001\",\"0000000000000000000000000000000000000000000000000000000000000000\":\"00000000000000000000000000000000000000000000000000000000000000de\"}\n");

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
        public DbSource<byte[]> storageDict() {
            LevelDbDataSource ds = new LevelDbDataSource("storageDict");
            ds.init();
            return ds;
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

        StorageDictionary dictionary = dictionaryDb.getDictionaryFor(Layout.Lang.solidity, address);
        Ast.Contract dataMembers = getContractAllDataMembers(youtubeViewsSource, "YoutubeViews");

        StoragePage storagePage = contractDataService.getContractData(address, new ContractData(dataMembers, dictionary), false, Path.empty(), 0, 20);
        List<StorageEntry> entries = storagePage.getEntries();

        assertNotNull(entries);
        assertFalse(entries.isEmpty());

        System.out.println(new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValueAsString(entries));
    }

    @Value("${classpath:contracts/real/ProjectKudos.sol}")
    private Resource projectKudosSource;

    @Test
    public void projectKudosTest() throws IOException {
        byte[] address = Hex.decode("bc4a3057325dfdde568f66ab70548df12d53aa85");

        StorageDictionary dictionary = dictionaryDb.getDictionaryFor(Layout.Lang.solidity, address);
        Ast.Contract dataMembers = getContractAllDataMembers(projectKudosSource, "ProjectKudos");

        Path path = Path.of("3", "000000000000000000000000297e5d5d48fe9cbaa8cf2094e82e7dcb377dddff");
        StoragePage storagePage = contractDataService.getContractData(address, new ContractData(dataMembers, dictionary), false, path, 0, 20);
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

        StorageDictionary dictionary = dictionaryDb.getDictionaryFor(Layout.Lang.solidity, address);
        Ast.Contract dataMembers = getContractAllDataMembers(etherPokerTableSource, "EtherPokerTable");

        StoragePage storagePage = contractDataService.getContractData(address, new ContractData(dataMembers, dictionary), false, Path.of(8, 0), 0, 20);
        List<StorageEntry> entries = storagePage.getEntries();

        assertNotNull(entries);
        assertEquals(4, entries.size());
        assertEquals(4, storagePage.getTotal());

        System.out.println(new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValueAsString(storagePage));
    }

    @Value("${classpath:contracts/struct/BoolTest.sol}")
    private Resource boolTestSource;

    @Test
    public void boolTestTest() throws IOException {
        byte[] address = Hex.decode("85a6ef0ae351abffb1200aa605cb7e3058072ae3");

        StorageDictionary dictionary = dictionaryDb.getDictionaryFor(Layout.Lang.solidity, address);
        Ast.Contract dataMembers = getContractAllDataMembers(boolTestSource, "BoolTest");

        StoragePage storagePage = contractDataService.getContractData(address, new ContractData(dataMembers, dictionary), false, Path.of(0), 0, 20);
        List<StorageEntry> entries = storagePage.getEntries();

        assertNotNull(entries);

        System.out.println(new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValueAsString(storagePage));
    }

    @Value("${classpath:contracts/struct/NestedStruct.sol}")
    private Resource nestedStructSource;

    @Test
    public void nestedStructTest() throws IOException {
        byte[] address = Hex.decode("ab7648c7664da59badeb9fa321b8111e6f29bc3e");


        StorageDictionary dictionary = dictionaryDb.getDictionaryFor(Layout.Lang.solidity, address);
        Ast.Contract dataMembers = getContractAllDataMembers(nestedStructSource, "NestedStruct");

        StoragePage storagePage = contractDataService.getContractData(address, new ContractData(dataMembers, dictionary), false, Path.of(1,1,1), 0, 20);
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
            String input = "[{\"key\":\"0000000000000000000000000000000000000000000000000000000000000000\",\"value\":\"00000000000000000000000000000000000000000000000000000000000000de\",\"type\":\"raw\"},{\"key\":\"0000000000000000000000000000000000000000000000000000000000000001\",\"value\":\"0000000000000000000000000000000000000000000000000000000000000001\",\"type\":\"raw\"},{\"key\":\"0000000000000000000000000000000000000000000000000000000000000002\",\"value\":\"726f6f74206c6576656c20737472756374000000000000000000000000000022\",\"type\":\"raw\"},{\"key\":\"0000000000000000000000000000000000000000000000000000000000000003\",\"value\":\"0000000000000000000000000000000000000000000000000000000000000001\",\"type\":\"raw\"},{\"key\":\"0000000000000000000000000000000000000000000000000000000000000004\",\"value\":\"0000000000000000000000000000000000000000000000000000000000000084\",\"type\":\"raw\"},{\"key\":\"0000000000000000000000000000000000000000000000000000000000000005\",\"value\":\"0000000000000000000000000000000000000000000000000000000000000001\",\"type\":\"raw\"}]";
            System.out.println(translate(input));
        }
    }
}
