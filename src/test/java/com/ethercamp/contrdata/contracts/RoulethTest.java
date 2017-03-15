package com.ethercamp.contrdata.contracts;

import com.ethercamp.contrdata.BaseTest;
import com.ethercamp.contrdata.ContractDataService;
import com.ethercamp.contrdata.contract.ContractData;
import com.ethercamp.contrdata.storage.StoragePage;
import com.ethercamp.contrdata.utils.ContractResourceLoader;
import com.ethercamp.contrdata.utils.RealContractResource;
import org.ethereum.util.blockchain.SolidityContract;
import org.ethereum.vm.DataWord;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.util.function.Function;

import static com.ethercamp.contrdata.storage.Path.empty;

public class RoulethTest extends BaseTest {

    private static class Contract {
        public static final String NAME = "Rouleth";
        public static final String ADDRESS = "18a672e11d637fffadccc99b152f4895da069601";
    }

    private static final String STATE_SERVICE_URL = "http://159.203.188.235:8080";
//    private static final String STATE_SERVICE_URL = "https://temp.ether.camp";

    @Ignore
    @Test
    public void downloadContractStorage() throws IOException {
        ContractResourceLoader.downloadStorage("/home/eshevchenko/projects/git/storage-dict/src/test/resources/contracts/real/Rouleth", STATE_SERVICE_URL, Contract.ADDRESS);
    }


    @Test
    public void test() throws IOException {
        RealContractResource cr = new RealContractResource(Contract.NAME, Contract.ADDRESS);

        Function<DataWord, DataWord> valueExtractor = cr.getValueExtractor();
        ContractData cd = cr.getContractData();
        contractDataService.fillMissingKeys(cd);

        ContractData.Element developer = getElement(cd, "developer");
        ContractData.Element profitSinceChange = getElement(cd, "profitSinceChange");
        ContractData.Element lossSinceChange = getElement(cd, "lossSinceChange");
        ContractData.Element setting_maxInvestors = getElement(cd, "setting_maxInvestors");

        System.out.println(developer.getValue(valueExtractor));
        System.out.println(profitSinceChange.getValue(valueExtractor));
        System.out.println(lossSinceChange.getValue(valueExtractor));
        System.out.println(setting_maxInvestors.getValue(valueExtractor));
    }

    @Autowired
    private ContractDataService contractDataService;

    @Test
    public void  test1() throws IOException {
        RealContractResource cr = new RealContractResource(Contract.NAME, Contract.ADDRESS);

        String src = cr.getSource();
        SolidityContract contract = blockchain.submitNewContract(src, Contract.NAME);


        ContractData cd = new ContractData(getContractAllDataMembers(src, Contract.NAME), cr.getStorageDictionary());

        StoragePage page = contractDataService.getContractData(contract.getAddress(), cd, false, empty(), 0, 1000);

        System.out.println(toJson(page));
    }


    @Test
    public void testStorage() throws IOException {
/*
        Map<DataWord, DataWord> map = new HashMap<DataWord, DataWord>() {{
            IntStream.range(0, 10).boxed().forEach(i -> put(new DataWord(i), new DataWord((i + 1) * 3)));
        }};

        map = new ObjectMapper().readValue(toJson(map), new TypeReference<Map<DataWord, DataWord>>() {
        });

        DataWord value = map.get(DataWord.ZERO);
        System.out.println(value);

        ContractResourceLoader resourceLoader = new ContractResourceLoader();
        map = resourceLoader.loadCompressedJsonAsObject("real", Contract.NAME, Contract.ADDRESS, "storage.gz");

        value = map.get(DataWord.ZERO);
        System.out.println(value);

        for (DataWord key : map.keySet()) {
            if (key.toString().endsWith(DataWord.ZERO.toString())) {
                System.out.println(key.equals(DataWord.ZERO));
            }
        }
*/
    }
}
