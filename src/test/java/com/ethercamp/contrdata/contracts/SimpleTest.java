package com.ethercamp.contrdata.contracts;

import com.ethercamp.contrdata.BaseTest;
import com.ethercamp.contrdata.ContractDataService;
import com.ethercamp.contrdata.contract.Ast;
import com.ethercamp.contrdata.contract.ContractData;
import com.ethercamp.contrdata.storage.Path;
import com.ethercamp.contrdata.storage.StorageEntry;
import com.ethercamp.contrdata.storage.StoragePage;
import com.ethercamp.contrdata.storage.dictionary.Layout;
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

    @Autowired
    private ContractDataService contractDataService;
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

        StoragePage page = contractDataService.getContractData(contract.getAddress(), cd, false, Path.of(1), 0, arrSize);
        List<StorageEntry> entries = page.getEntries();

        for (int i = 0; i < arrSize; i++) {
            boolean expected = setToTrue.contains(i);

            String value = getElement(cd, "array[%d]", i).getValue(extractor);
            Assert.assertEquals(expected, Boolean.valueOf(value));

            value = ((StorageEntry.Value) entries.get(i).getValue()).getDecoded();
            Assert.assertEquals(expected, Boolean.valueOf(value));
        }
    }
}
