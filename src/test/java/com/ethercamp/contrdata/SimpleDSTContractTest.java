package com.ethercamp.contrdata;

import com.ethercamp.contrdata.contract.Ast;
import com.ethercamp.contrdata.contract.ContractData;
import com.ethercamp.contrdata.contract.Member;
import com.ethercamp.contrdata.storage.Path;
import com.ethercamp.contrdata.storage.StorageEntry;
import com.ethercamp.contrdata.storage.StoragePage;
import com.ethercamp.contrdata.storage.dictionary.StorageDictionary;
import com.ethercamp.contrdata.storage.dictionary.StorageDictionaryDb;
import org.ethereum.core.BlockchainImpl;
import org.ethereum.util.ByteUtil;
import org.ethereum.util.blockchain.SolidityContract;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SimpleDSTContractTest extends BaseTest {

    @Autowired
    private ContractDataService contractDataService;
    private String source;

    @Value("${classpath:contracts/real/SimpleDSTContract.sol}")
    public void setSource(Resource source) throws IOException {
        this.source = resourceToString(source);
    }

    @Test
    public void test() throws IOException {

        SolidityContract contract = blockchain.submitNewContract(source, "DSTContract");
        contract.callFunction("submitHKGProposal", 102, "test2");
        contract.callFunction("redeemProposalFunds", 0);
        printStorage(contract);

        Ast.Contract astContract = getContractAllDataMembers(source, "DSTContract");
        StorageDictionary dictionary = dictDb.getOrCreate(StorageDictionaryDb.Layout.Solidity, contract.getAddress());
        ContractData contractData = new ContractData(astContract, dictionary);


        Member proposals = contractData.getMembers().findByName("proposals");

        StoragePage page = contractDataService.getContractData(contract.getAddress(), contractData, false, Path.of(proposals.getStorageIndex()), 0, 100);
        List<StorageEntry> entries = page.getEntries();
        assertEquals(1, entries.size());

        Path path = Path.parse(((StorageEntry.Key) entries.get(0).getKey()).getPath());
        page = contractDataService.getContractData(contract.getAddress(), contractData, false, path, 0, 100);
        entries = page.getEntries();

        System.out.println(toJson(entries));

        Optional<StorageEntry> found = entries.stream()
                .filter(entry -> "redeemed".equals(((StorageEntry.Key) entry.getKey()).getDecoded()))
                .findFirst();

        assertTrue(found.isPresent());
        StorageEntry.Value value = (StorageEntry.Value) found.get().getValue();
        assertTrue(Boolean.valueOf(value.getDecoded()));
    }


    private void printStorage(SolidityContract contract) {
        byte[] address = contract.getAddress();
        System.out.printf("'%s' contract storage:\n", ByteUtil.toHexString(address));
        ((BlockchainImpl) blockchain.getBlockchain()).getRepository().getContractDetails(address).getStorage().entrySet().stream()
                .sorted((o1, o2) -> o1.getKey().compareTo(o2.getKey()))
                .forEach(e -> System.out.printf("'%s' : '%s'\n", e.getKey(), e.getValue()));
    }
}
