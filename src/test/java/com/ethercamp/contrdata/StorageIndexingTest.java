package com.ethercamp.contrdata;

import com.ethercamp.contrdata.contract.Ast;
import com.ethercamp.contrdata.contract.ContractData;
import com.ethercamp.contrdata.contract.Member;
import com.ethercamp.contrdata.contract.Members;
import com.ethercamp.contrdata.storage.dictionary.Layout;
import org.ethereum.util.blockchain.SolidityContract;
import org.ethereum.vm.DataWord;
import org.junit.Test;
import org.spongycastle.util.encoders.Hex;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.List;
import java.util.function.Function;

import static org.junit.Assert.*;

public class StorageIndexingTest extends BaseTest {

    @Value("classpath:contracts/PackingTest.sol")
    private Resource packingTest1Sol;
    @Value("classpath:contracts/TestBoolAfterPackedStruct.sol")
    private Resource packingTest2Sol;

    @Test
    public void packingTest1() throws IOException {

        SolidityContract contract = blockchain.submitNewContract(resourceToString(packingTest1Sol));
        blockchain.createBlock();

        Ast.Contract astContract = getContractAllDataMembers(packingTest1Sol, "PackingTest");
        ContractData contractData = new ContractData(astContract, dictDb.getDictionaryFor(Layout.Lang.solidity, contract.getAddress()));

        Members members = contractData.getMembers();
        assertNotNull(members);
        assertEquals(3, members.size());

        Function<DataWord, DataWord> valueExtractor = newValueExtractor(contract);

        Member mStruct = members.findByName("mStruct");
        assertEquals(0, mStruct.getStorageIndex());

        ContractData.Element mStructElement = contractData.elementByPath(mStruct.getPosition());
        mStructElement.getChildren(0, 10).stream().forEach(field -> {
            DataWord value = field.getStorageValue(valueExtractor);
            System.out.println(value);
        });

        Member mBool = members.findByName("mBool");
        assertEquals(2, mBool.getStorageIndex());
        ContractData.Element mBoolElement = contractData.elementByPath(mBool.getPosition());
        assertEquals(new DataWord(1), mBoolElement.getStorageValue(valueExtractor));


        Member mAddress = members.findByName("mAddress");
        assertEquals(2, mAddress.getStorageIndex());
        ContractData.Element mAddressElement = contractData.elementByPath(mAddress.getPosition());
        assertEquals(new DataWord("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"), mAddressElement.getStorageValue(valueExtractor));


    }

    @Test
    public void packingTest2() throws IOException {
        Ast.Contract contract = getContractAllDataMembers(packingTest2Sol, "TestBoolAfterPackedStruct");
        ContractData contractData = new ContractData(contract, dictDb.getDictionaryFor(Layout.Lang.solidity, Hex.decode("")));
        Members members = contractData.getMembers();

        assertNotNull(members);
        assertEquals(2, members.size());
        assertEquals(0, members.findByName("mStruct").getStorageIndex());
        assertEquals(1, members.findByName("mBool").getStorageIndex());

        DataWord packedStruct = new DataWord("00000000000000000000001c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c00");

        Members structFields = contractData.getStructFields("Struct");
        DataWord fBool = structFields.findByName("fBool").extractValue(packedStruct);
        assertTrue(fBool.isZero());

        DataWord addr = structFields.findByName("fAddr").extractValue(packedStruct);
        assertTrue(addr.toString().endsWith("1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c1c"));
    }


    @Value("classpath:contracts/TestNestedStruct.sol")
    private Resource nestingTestSol;

    @Test
    public void nestingTest() throws IOException {
        SolidityContract contract = blockchain.submitNewContract(resourceToString(nestingTestSol));
        blockchain.createBlock();

        Ast.Contract astContract = getContractAllDataMembers(nestingTestSol, "TestNestedStruct");
        ContractData contractData = new ContractData(astContract, dictDb.getDictionaryFor(Layout.Lang.solidity, contract.getAddress()));
        Function<DataWord, DataWord> valueExtractor = newValueExtractor(contract);

        ContractData.Element element = contractData.elementByPath();
        List<ContractData.Element> members = element.getAllChildren();
        assertEquals(6, members.size());

        ContractData.Element single = members.get(0);
        assertNotNull(single);
        List<ContractData.Element> marriageFields = single.getAllChildren();

        assertPersonEquals(marriageFields.get(0), valueExtractor, "abababababababababababababababababababab", "Ann");
        assertPersonEquals(marriageFields.get(1), valueExtractor, "cfcfcfcfcfcfcfcfcfcfcfcfcfcfcfcfcfcfcfcf", "Eugene");

        assertFieldSeparator(members.get(1), valueExtractor, "sep1");

        ContractData.Element dynArray = members.get(2);
        assertNotNull(dynArray);
        assertEquals(0, dynArray.getChildrenCount());

        assertFieldSeparator(members.get(3), valueExtractor, "sep2");

        ContractData.Element staticArray = members.get(4);
        assertEquals(1, staticArray.getChildrenCount());
        ContractData.Element staticEl1 = staticArray.getAllChildren().get(0);
        assertEquals("1", staticEl1.getKey());
        marriageFields = staticEl1.getAllChildren();
        assertPersonEquals(marriageFields.get(0), valueExtractor, "ffffabababababababababababababababababab", "Ann-1");
        assertPersonEquals(marriageFields.get(1), valueExtractor, "ffffcfcfcfcfcfcfcfcfcfcfcfcfcfcfcfcfcfcf", "Eugene-1");

        assertFieldSeparator(members.get(5), valueExtractor, "sep3");
    }

    private static void assertFieldSeparator(ContractData.Element fieldEl, Function<DataWord, DataWord> valueExtractor, String name) {
        assertFieldEqual(fieldEl, valueExtractor, "uint", name, "256");
    }

    protected static void assertPersonEquals(ContractData.Element person, Function<DataWord, DataWord> valueExtractor, String address, String name) {
        assertNotNull(person);
        List<ContractData.Element> fields = person.getAllChildren();
        assertNotNull(fields);
        assertEquals(2, fields.size());
        assertFieldEqual(fields.get(0), valueExtractor, "address", "addr", address);
        assertFieldEqual(fields.get(1), valueExtractor, "string", "name", name);
    }
}
