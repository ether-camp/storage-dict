package com.ethercamp.contrdata;

import com.ethercamp.contrdata.contract.Ast;
import com.ethercamp.contrdata.contract.ContractData;
import com.ethercamp.contrdata.storage.dictionary.Layout;
import org.ethereum.util.blockchain.SolidityContract;
import org.ethereum.vm.DataWord;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.List;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class StructTest extends BaseTest {


    private String contractSource;

    @Value("${classpath:contracts/struct/TestStruct.sol}")
    public void setContractSource(Resource contractSource) throws IOException {
        this.contractSource = resourceToString(contractSource);
    }

    @Test
    public void test() throws IOException {

        SolidityContract contract = blockchain.submitNewContract(contractSource);
        blockchain.createBlock();

        Ast.Contract astContract = getContractAllDataMembers(contractSource, "TestStruct");

        Function<DataWord, DataWord> valueExtractor = newValueExtractor(contract);
        ContractData contractData = new ContractData(astContract, dictDb.getDictionaryFor(Layout.Lang.solidity, contract.getAddress()));

        ContractData.Element element = contractData.elementByPath();
        List<ContractData.Element> members = element.getChildren(0, 20);

        assertNotNull(members);
        assertEquals(2, members.size());

        ContractData.Element wife = members.get(0);
        assertNotNull(wife);
        assertEquals("wife", wife.getKey());
        assertStructEqual(wife, valueExtractor, "Angelina", "Jolie", "40", "abcdefabcdefabcdefabcdefabcdefabcdefabcd");
        assertEquals(wife, contractData.elementByPath(wife.path().parts()));

        ContractData.Element husband = members.get(1);
        assertNotNull(husband);
        assertEquals("husband", husband.getKey());
        assertStructEqual(husband, valueExtractor, "Brad", "Pitt", "53", "1234567890123456789012345678901234567890");
        assertEquals(husband, contractData.elementByPath(husband.path().parts()));
    }
}
