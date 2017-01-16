package com.ethercamp.contrdata;

import com.ethercamp.contrdata.contract.Ast;
import com.ethercamp.contrdata.contract.ContractData;
import com.ethercamp.contrdata.storage.Path;
import com.ethercamp.contrdata.storage.StorageEntry;
import com.ethercamp.contrdata.storage.dictionary.StorageDictionary;
import com.ethercamp.contrdata.storage.dictionary.StorageDictionaryDb;
import com.ethercamp.contrdata.storage.dictionary.StorageDictionaryVmHook;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.ethereum.datasource.DbSource;
import org.ethereum.util.blockchain.SolidityContract;
import org.ethereum.vm.VM;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.List;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;

public class ContractDataServiceTest extends BaseTest {

    @Autowired
    private ContractDataService contractDataService;

    @Value("classpath:contracts/TestNestedStruct.sol")
    private Resource nestingTestSol;

    @Autowired
    private DbSource<byte[]> storageDict;

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
    public void index_shouldFillMissing() throws IOException {
        final Ast.Contract astContract = getContractAllDataMembers(nestingTestSol, "TestNestedStruct");

        VM.setVmHook(null);

        {
            final SolidityContract contract = blockchain.submitNewContract(resourceToString(nestingTestSol));
            blockchain.createBlock();

            final StorageDictionary dictionary = dictDb.getOrCreate(StorageDictionaryDb.Layout.Solidity, contract.getAddress());
//            dumpStorageDict(dictionary);
//            clearStorageDict();

            final ContractData contractData = new ContractData(astContract, dictionary);

            contractDataService.fillMissingKeys(contractData);
//            dictionary.store();
            dumpStorageDict(dictionary);

            final List<StorageEntry> entries = contractDataService.getContractData(contract.getAddress(), contractData, false, Path.empty(), 0, 20).getEntries();

            assertThat(entries.size(), is(8));
            {
                final StorageEntry entry = entries.get(0);
                final StorageEntry.Value value = (StorageEntry.Value) entry.getValue();
                final StorageEntry.Key key = (StorageEntry.Key) entry.getKey();
                assertThat(key.getDecoded(), is("simple1"));
                assertThat(value.getDecoded(), is("2"));
            }
            {
                final StorageEntry entry = entries.get(7);
                final StorageEntry.Value value = (StorageEntry.Value) entry.getValue();
                final StorageEntry.Key key = (StorageEntry.Key) entry.getKey();
                assertThat(key.getDecoded(), is("simple2"));
                assertThat(value.getDecoded(), is("3"));
            }

        }
    }

    @Test
    public void dumpTest() throws IOException {

        SolidityContract contract = blockchain.submitNewContract(resourceToString(nestingTestSol));
        blockchain.createBlock();
        StorageDictionary dictionary = dictDb.getOrCreate(StorageDictionaryDb.Layout.Solidity, contract.getAddress());

        assertFalse(contractDataService.storageEntries(contract.getAddress()).isEmpty());
        assertFalse(dictionary.dmp().isEmpty());
    }

    private void clearStorageDict() {
        ((HashMapDBExt) storageDict).source().clear();
    }

    private void dumpStorageDict(StorageDictionary dictionary) {
        System.out.println("Test Dictionary dump " + dictionary.dump());
    }
}