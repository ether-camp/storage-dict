package com.ethercamp.contrdata;

import com.ethercamp.contrdata.contract.Ast;
import com.ethercamp.contrdata.contract.ContractData;
import com.ethercamp.contrdata.storage.Path;
import com.ethercamp.contrdata.storage.Storage;
import com.ethercamp.contrdata.storage.StorageEntry;
import com.ethercamp.contrdata.storage.StoragePage;
import com.ethercamp.contrdata.storage.dictionary.Layout;
import com.ethercamp.contrdata.storage.dictionary.StorageDictionary;
import org.ethereum.db.ContractDetails;
import org.ethereum.util.blockchain.SolidityContract;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SimpleDSTContractTest extends BaseTest {

    @Autowired
    private ContractDataService contractDataService;
    @Autowired
    private Storage storage;

    private String source;

    @Value("${classpath:contracts/real/SimpleDSTContract.sol}")
    public void setSource(Resource source) throws IOException {
        this.source = resourceToString(source);
    }

    @Test
    public void test() throws IOException {

        System.out.println("DSTContract creating:");
        SolidityContract contract = blockchain.submitNewContract(source, "DSTContract");
//        printStorage(contract, storage);

        StorageDictionary dictionary = dictDb.getDictionaryFor(Layout.Lang.solidity, contract.getAddress());
        printDictionary(contract, dictionary);

        System.out.println("submitHKGProposal calling:");
        contract.callFunction("submitHKGProposal", 102, "test2");
//        printStorage(contract, storage);
        printDictionary(contract, dictionary);

        System.out.println("redeemProposalFunds calling:");
        contract.callFunction("redeemProposalFunds", 0);
//        printStorage(contract, storage);
        printDictionary(contract, dictionary);


        Ast.Contract astContract = getContractAllDataMembers(source, "DSTContract");
        dictionary = dictDb.getDictionaryFor(Layout.Lang.solidity, contract.getAddress());
        ContractData contractData = new ContractData(astContract, dictionary);

        Path path = Path.of(contractData.getMembers().findByName("proposals").getStorageIndex());
        System.out.printf("Getting contract data by path[%s]:\n", path);
        StoragePage page = contractDataService.getContractData(contract.getAddress(), contractData, false, path, 0, 100);
        System.out.println(toJson(page));

        assertEquals(1, page.getEntries().size());

        path = Path.parse(((StorageEntry.Key) page.getEntries().get(0).getKey()).getPath());
        System.out.printf("Getting contract data by path[%s]:\n", path);
        page = contractDataService.getContractData(contract.getAddress(), contractData, false, path, 0, 100);
        System.out.println(toJson(page));

        Optional<StorageEntry> found = page.getEntries().stream()
                .filter(entry -> "redeemed".equals(((StorageEntry.Key) entry.getKey()).getDecoded()))
                .findFirst();

        assertTrue(found.isPresent());
        StorageEntry.Value value = (StorageEntry.Value) found.get().getValue();
        assertTrue(Boolean.valueOf(value.getDecoded()));
    }

    private void printDictionary(SolidityContract contract, StorageDictionary dictionary) {
        StorageDictionary.PathElement root = dictionary.getByPath();
        ContractDetails details = (blockchain.getBlockchain()).getRepository().getContractDetails(contract.getAddress());
        System.out.println(root.toString(details, 2));
    }
}
