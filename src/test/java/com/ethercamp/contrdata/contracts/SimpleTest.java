package com.ethercamp.contrdata.contracts;

import com.ethercamp.contrdata.BaseTest;
import com.ethercamp.contrdata.ContractDataService;
import com.ethercamp.contrdata.contract.Ast;
import com.ethercamp.contrdata.contract.ContractData;
import com.ethercamp.contrdata.storage.Path;
import com.ethercamp.contrdata.storage.StoragePage;
import com.ethercamp.contrdata.storage.dictionary.Layout;
import com.ethercamp.contrdata.utils.ContractInfo;
import com.ethercamp.contrdata.utils.RealContractResource;
import org.ethereum.util.blockchain.SolidityContract;
import org.ethereum.vm.DataWord;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.List;
import java.util.function.Function;

import static java.util.Arrays.asList;

public class SimpleTest extends BaseTest {

    private String source;

    @Value("${classpath:contracts/real/Simple/contract.sol}")
    public void setSource(Resource source) throws IOException {
        this.source = resourceToString(source);
    }

    @Test
    public void test() throws IOException {
        SolidityContract contract = blockchain.submitNewContract(source, "Simple");
        printStorageInfo(contract);

        Ast.Contract ast = getContractAllDataMembers(source, "Simple");
        ContractData cd = new ContractData(ast, dictDb.getDictionaryFor(Layout.Lang.solidity, contract.getAddress()));

        Function<DataWord, DataWord> extractor = newValueExtractor(contract);

        final int arrSize = 35;
        final List<Integer> setToTrue = asList(1, 15, 33);

        for (int i = 0; i < arrSize; i++) {
            String value = getElement(cd, "array[%d]", i).getValue(extractor);
            Assert.assertEquals(setToTrue.contains(i), Boolean.valueOf(value));
        }
    }
}
