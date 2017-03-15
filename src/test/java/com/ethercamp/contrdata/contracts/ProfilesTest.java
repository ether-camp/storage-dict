package com.ethercamp.contrdata.contracts;

import com.ethercamp.contrdata.BaseTest;
import com.ethercamp.contrdata.ContractDataService;
import com.ethercamp.contrdata.contract.Ast;
import com.ethercamp.contrdata.contract.ContractData;
import com.ethercamp.contrdata.storage.Path;
import com.ethercamp.contrdata.storage.StoragePage;
import com.ethercamp.contrdata.storage.dictionary.Layout;
import com.ethercamp.contrdata.storage.dictionary.StorageDictionary;
import org.ethereum.util.blockchain.SolidityCallResult;
import org.ethereum.util.blockchain.SolidityContract;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;

import java.io.IOException;

public class ProfilesTest extends BaseTest {

    @Autowired
    private ContractDataService contractDataService;
    private String source;

    @Value("${classpath:contracts/Profiles.sol}")
    public void setSource(Resource source) throws IOException {
        this.source = resourceToString(source);
    }

    @Test
    public void test() throws IOException {
        SolidityContract contract = blockchain.submitNewContract(source);
        printStorageInfo(contract);

        SolidityCallResult callResult = contract.callFunction("addMale", "John Doe", 29);
        byte[] address = (byte[]) callResult.getReturnValue();
        printStorageInfo(contract);

        callResult = contract.callFunction("toggleResidence", address);
        Assert.assertTrue((Boolean) callResult.getReturnValue());
        printStorageInfo(contract);

        Ast.Contract ast = getContractAllDataMembers(source, "Profiles");
        StorageDictionary dictionary = dictDb.getDictionaryFor(Layout.Lang.solidity, contract.getAddress());

        ContractData contractData = new ContractData(ast, dictionary);
        StoragePage page = contractDataService.getContractData(contract.getAddress(), contractData, false, Path.of(0, "0000000000000000000000005db10750e8caff27f906b41c71b3471057dd2004"), 0, 100);
        System.out.println(toJson(page));
    }
}
