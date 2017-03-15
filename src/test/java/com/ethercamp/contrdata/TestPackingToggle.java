package com.ethercamp.contrdata;

import com.ethercamp.contrdata.contract.Ast;
import com.ethercamp.contrdata.contract.ContractData;
import com.ethercamp.contrdata.storage.Path;
import com.ethercamp.contrdata.storage.StorageEntry;
import com.ethercamp.contrdata.storage.StoragePage;
import com.ethercamp.contrdata.storage.dictionary.Layout;
import com.ethercamp.contrdata.storage.dictionary.StorageDictionary;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.ethereum.util.blockchain.SolidityCallResult;
import org.ethereum.util.blockchain.SolidityContract;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.List;

public class TestPackingToggle extends BaseTest {

    @Autowired
    private ContractDataService contractDataService;
    private String source;

    @Value("${classpath:contracts/PackingTest.sol}")
    public void setSource(Resource source) throws IOException {
        this.source = resourceToString(source);
    }

    @Test
    public void test() throws IOException {
        SolidityContract contract = blockchain.submitNewContract(source, "PackingTest");

        Ast.Contract astContract = getContractAllDataMembers(source, "PackingTest");
        StorageDictionary dictionary = dictDb.getDictionaryFor(Layout.Lang.solidity, contract.getAddress());
        ContractData contractData = new ContractData(astContract, dictionary);

        StoragePage page = contractDataService.getContractData(contract.getAddress(), contractData, false, Path.empty(), 0, 100);
        List<StorageEntry> entries = page.getEntries();

        System.out.println(new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValueAsString(entries));

        SolidityCallResult callResult = contract.callFunction("toggle");
        
        System.out.println(callResult);

        callResult = contract.callFunction("toggle");
        
        System.out.println(callResult);

    }
}
