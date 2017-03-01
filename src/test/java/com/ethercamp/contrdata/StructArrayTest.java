package com.ethercamp.contrdata;

import com.ethercamp.contrdata.contract.Ast;
import com.ethercamp.contrdata.contract.ContractData;
import com.ethercamp.contrdata.storage.Path;
import com.ethercamp.contrdata.storage.Storage;
import com.ethercamp.contrdata.storage.StoragePage;
import com.ethercamp.contrdata.storage.dictionary.Layout;
import com.ethercamp.contrdata.storage.dictionary.StorageDictionaryDb;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.ethereum.util.blockchain.SolidityContract;
import org.ethereum.vm.DataWord;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.spongycastle.util.encoders.Hex.toHexString;

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
        ContractData contractData = new ContractData(contractAst, dictDb.getDictionaryFor(Layout.Lang.solidity, contract.getAddress()));
        contractData.elementByPath(0);

        ContractData.Element targetElement = contractData.elementByPath(0);

        assertEquals(2, targetElement.getChildrenCount());

        Function<DataWord, DataWord> valueExtractor = newValueExtractor(contract);
        targetElement.getChildren(0, 20).forEach(child -> System.out.println(child.getStorageValue(valueExtractor)));
    }

    @Autowired
    private Storage storage;
    @Autowired
    private ContractDataService contractDataService;

    private String shiftedArraySrc;

    @Value("${classpath:contracts/struct/ShiftedArray.sol}")
    public void setShiftedArraySrc(Resource contractSource) throws IOException {
        this.shiftedArraySrc = resourceToString(contractSource);
    }

    @Test
    public void testShiftedArray() throws IOException {

        SolidityContract contract = blockchain.submitNewContract(shiftedArraySrc);
        blockchain.createBlock();

        Ast.Contract contractAst = getContractAllDataMembers(shiftedArraySrc, "ShiftedArray");
        StoragePage page = contractDataService.getContractData(toHexString(contract.getAddress()), contractAst.toJson(), Path.of(0), 0, 100);

        System.out.println(new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValueAsString(page));
        Set<DataWord> keys = storage.keys(contract.getAddress());


        System.out.println(storage.size(contract.getAddress()));
        storage.entries(contract.getAddress(), new ArrayList<>(keys)).entrySet().stream()
                .sorted((e1, e2) -> e1.getKey().compareTo(e2.getKey()))
                .forEach(System.out::println);
    }


    private String removedFieldsSrc;

    @Value("${classpath:contracts/RemovedFields.sol}")
    public void setRemovedFieldsSrc(Resource contractSource) throws IOException {
        this.removedFieldsSrc = resourceToString(contractSource);
    }

    @Test
    public void testRemovedFields() throws IOException {
        SolidityContract contract = blockchain.submitNewContract(removedFieldsSrc);
        blockchain.createBlock();

        Ast.Contract contractAst = getContractAllDataMembers(removedFieldsSrc, "RemovedFields");
        StoragePage page = contractDataService.getContractData(toHexString(contract.getAddress()), contractAst.toJson(), Path.of(0), 0, 100);

        System.out.println(new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValueAsString(page));
    }

}
