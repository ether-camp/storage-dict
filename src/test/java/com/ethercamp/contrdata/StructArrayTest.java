package com.ethercamp.contrdata;

import com.ethercamp.contrdata.bc.SolidityContract;
import com.ethercamp.contrdata.contract.Ast;
import com.ethercamp.contrdata.contract.ContractData;
import com.ethercamp.contrdata.storage.dictionary.StorageDictionaryDb;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

public class StructArrayTest extends BaseTest {

    private String contractSource;

    @Value("${classpath:contracts/struct/TestStructArray.sol}")
    public void setContractSource(Resource contractSource) throws IOException {
        this.contractSource = resourceToString(contractSource);
    }

    @Test
    public void test() throws IOException {

        SolidityContract contract = blockchain.submitNewContract(contractSource);
        blockchain.createBlock();

        Ast.Contract contractAst = getContractAllDataMembers(contractSource, "TestStructArray");
        ContractData contractData = new ContractData(contractAst, dictDb.getOrCreate(StorageDictionaryDb.Layout.Solidity, contract.getAddress()));
        contractData.elementByPath(0);

        ContractData.Element targetElement = contractData.elementByPath(0);
        ;
        assertEquals(2, targetElement.getChildrenCount());


        targetElement.getChildren(0, 20).forEach(child -> {
            System.out.println(child.getStorageValue(dataWord -> contract.getStorage().get(dataWord)));
        });
    }
}
