package com.ethercamp.contrdata;

import com.ethercamp.contrdata.contract.Ast;
import com.ethercamp.contrdata.contract.ContractData;
import com.ethercamp.contrdata.storage.Path;
import com.ethercamp.contrdata.storage.StorageEntry;
import com.ethercamp.contrdata.storage.dictionary.Layout;
import com.ethercamp.contrdata.storage.dictionary.StorageDictionary;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.ethereum.util.blockchain.SolidityContract;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class ContractDataServiceTest extends BaseTest {

    @Autowired
    private ContractDataService contractDataService;

    @Value("classpath:contracts/TestDictRecovery.sol")
    private Resource dictRecoveryTestSol;
    @Value("classpath:contracts/TestNestedStruct.sol")
    private Resource nestingTestSol;
    @Value("classpath:contracts/EmptyContract.sol")
    private Resource emptyContractSol;

    @Test
    public void test() throws IOException {

        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

        SolidityContract contract = blockchain.submitNewContract(resourceToString(nestingTestSol));
        blockchain.createBlock();

        Ast.Contract astContract = getContractAllDataMembers(nestingTestSol, "TestNestedStruct");
        StorageDictionary dictionary = dictDb.getDictionaryFor(Layout.Lang.solidity, contract.getAddress());

        List<StorageEntry> entries = contractDataService.getContractData(contract.getAddress(), new ContractData(astContract, dictionary), false, Path.empty(), 0, 20).getEntries();
        System.out.println(mapper.writeValueAsString(entries));

        entries = contractDataService.getStorageEntries(contract.getAddress(), 0, 20).getEntries();
        System.out.println(mapper.writeValueAsString(entries));

        entries = contractDataService.getStorageEntries(contract.getAddress(), 0, 20).getEntries();
        System.out.println(mapper.writeValueAsString(entries));
    }

    @Test
    public void importTest() throws IOException {

        Resource sourceSol = this.nestingTestSol;
        Resource targetSol = this.emptyContractSol;

        SolidityContract source = blockchain.submitNewContract(resourceToString(sourceSol));
        SolidityContract target = blockchain.submitNewContract(resourceToString(targetSol));
        blockchain.createBlock();

        Map<String, String> dump = contractDataService.exportDictionary(source.getAddress(), Path.empty());
        contractDataService.importDictionary(target.getAddress(), dump);

        ContractData sourceContractData = getContractData(source.getAddress(), sourceSol, "TestNestedStruct");
        List<StorageEntry> sourceEntries = contractDataService.getContractData(target.getAddress(), sourceContractData, false, Path.empty(), 0, Integer.MAX_VALUE).getEntries();

        ContractData targetContractData = getContractData(target.getAddress(), sourceSol, "TestNestedStruct");
        List<StorageEntry> targetEntries = contractDataService.getContractData(target.getAddress(), targetContractData, false, Path.empty(), 0, Integer.MAX_VALUE).getEntries();

        assertEquals(sourceEntries.size(), targetEntries.size());
        for (int i = 0; i < sourceEntries.size(); i++) {
            assertEquals(sourceEntries.get(i), targetEntries.get(i));
        }
    }

    private ContractData getContractData(byte[] address, Resource source, String contractName) throws IOException {
        Ast.Contract contract = getContractAllDataMembers(source, contractName);
        StorageDictionary dictionary = dictDb.getDictionaryFor(Layout.Lang.solidity, address);

        return new ContractData(contract, dictionary);
    }

    @Test
    public void dumpTest() throws IOException {

        SolidityContract contract = blockchain.submitNewContract(resourceToString(nestingTestSol));
        blockchain.createBlock();
        StorageDictionary dictionary = dictDb.getDictionaryFor(Layout.Lang.solidity, contract.getAddress());

        assertFalse(contractDataService.storageEntries(contract.getAddress()).isEmpty());
        assertFalse(dictionary.dmp().isEmpty());
    }
}