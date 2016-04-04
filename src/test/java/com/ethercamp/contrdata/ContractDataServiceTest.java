package com.ethercamp.contrdata;

import com.ethercamp.contrdata.bc.SolidityContract;
import com.ethercamp.contrdata.contract.Ast;
import com.ethercamp.contrdata.contract.ContractData;
import com.ethercamp.contrdata.storage.Path;
import com.ethercamp.contrdata.storage.StorageEntry;
import com.ethercamp.contrdata.storage.dictionary.StorageDictionary;
import com.ethercamp.contrdata.storage.dictionary.StorageDictionaryDb;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertFalse;

public class ContractDataServiceTest extends BaseTest {

    @Autowired
    private ContractDataService contractDataService;
    @Value("classpath:contracts/TestNestedStruct.sol")
    private Resource nestingTestSol;

    @Test
    public void test() throws IOException {

        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

        SolidityContract contract = blockchain.submitNewContract(resourceToString(nestingTestSol));
        blockchain.createBlock();

        Ast.Contract astContract = getContractAllDataMembers(nestingTestSol, "TestNestedStruct");
        StorageDictionary dictionary = dictDb.getOrCreate(StorageDictionaryDb.Layout.Solidity, contract.getAddress());

        List<StorageEntry> entries = contractDataService.getContractData(contract.getAddress(), new ContractData(astContract, dictionary), false, Path.empty(), 0, 20).getEntries();
        System.out.println(mapper.writeValueAsString(entries));

        entries = contractDataService.getStorageEntries(contract.getAddress(), 0, 20).getEntries();
        System.out.println(mapper.writeValueAsString(entries));

        entries = contractDataService.getStorageEntries(contract.getAddress(), 0, 20).getEntries();
        System.out.println(mapper.writeValueAsString(entries));
    }

    @Test
    public void dumpTest() throws IOException {

        SolidityContract contract = blockchain.submitNewContract(resourceToString(nestingTestSol));
        blockchain.createBlock();
        StorageDictionary dictionary = dictDb.getOrCreate(StorageDictionaryDb.Layout.Solidity, contract.getAddress());

        assertFalse(contractDataService.storageEntries(contract.getAddress()).isEmpty());
        assertFalse(dictionary.dmp().isEmpty());
    }
}