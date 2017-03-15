package com.ethercamp.contrdata;

import com.ethercamp.contrdata.contract.ContractData;
import org.ethereum.util.blockchain.SolidityCallResult;
import org.ethereum.util.blockchain.SolidityContract;
import org.ethereum.vm.DataWord;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.math.BigInteger;
import java.util.function.Function;

import static com.ethercamp.contrdata.utils.RandomUtils.randomBytes;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class StructArrayTest extends BaseTest {

    private String testStructArraySrc;

    @Value("${classpath:contracts/struct/TestStructArray.sol}")
    public void setTestStructArray(Resource source) throws IOException {
        this.testStructArraySrc = resourceToString(source);
    }

    enum Gender {
        MALE, FEMALE
    }

    @Test
    public void testStructArray() throws IOException {
        SolidityContract contract = blockchain.submitNewContract(testStructArraySrc);

        SolidityCallResult callResult = contract.callFunction("addPerson", "Angelina Jolie", 29, Gender.FEMALE.ordinal());
        BigInteger wifeId = (BigInteger) callResult.getReturnValue();

        callResult = contract.callFunction("addPerson", "Brad Pitt", 28, Gender.MALE.ordinal());
        BigInteger husbandId = (BigInteger) callResult.getReturnValue();

        callResult = contract.callFunction("addMarriage", wifeId, husbandId);
        BigInteger marriageId = (BigInteger) callResult.getReturnValue();


        printStorageInfo(contract);
        ContractData cd = getContractData(contract, testStructArraySrc, "TestStructArray");
        Function<DataWord, DataWord> valueExtractor = newValueExtractor(contract);

        ContractData.Element personsEl = getElement(cd, "persons");
        assertEquals(2, personsEl.getChildrenCount());

        ContractData.Element wifeEl = getElement(cd, "persons[%s]", wifeId);
        assertStructEqual(wifeEl, valueExtractor, "Angelina Jolie", 29, Gender.FEMALE);

        ContractData.Element husbandEl = getElement(cd, "persons[%s]", husbandId);
        assertStructEqual(husbandEl, valueExtractor, "Brad Pitt", 28, Gender.MALE);


        ContractData.Element registerEl = getElement(cd, "register");
        assertEquals(1, registerEl.getChildrenCount());

        assertEquals(wifeId.toString(), getElement(cd, "register[%s].wife", marriageId).getValue(valueExtractor));
        assertEquals(husbandId.toString(), getElement(cd, "register[%s].husband", marriageId).getValue(valueExtractor));
        assertNotNull(getElement(cd, "register[%s].marriageDate", marriageId).getValue(valueExtractor));
    }

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
        contract.callFunction("newProposal",
                randomBytes(20),
                123231,
                "prop desc",
                randomBytes(3),
                1000000,
                true
        );
        contract.callFunction("newProposal",
                randomBytes(20),
                123233,
                "prop desc 1",
                randomBytes(3),
                1000001,
                true
        );

        printStorageInfo(contract);

        ContractData contractData = getContractData(contract, shiftedArraySrc, "ShiftedArray");
        ContractData.Element proposals = getElement(contractData, "proposals");
        assertEquals(2, proposals.getChildrenCount());
    }


    private String removedFieldsSrc;

    @Value("${classpath:contracts/RemovedFields.sol}")
    public void setRemovedFieldsSrc(Resource contractSource) throws IOException {
        this.removedFieldsSrc = resourceToString(contractSource);
    }

    @Test
    public void testRemovedFields() throws IOException {
        SolidityContract contract = blockchain.submitNewContract(removedFieldsSrc);

        ContractData cd = getContractData(contract, removedFieldsSrc, "RemovedFields");
        Function<DataWord, DataWord> valueExtractor = newValueExtractor(contract);
        ContractData.Element addresses = getElement(cd, "addresses");

        assertEquals(3, addresses.getChildrenCount());

        System.out.println(getElement(cd, "addresses[0]").getValue(valueExtractor));
        System.out.println(getElement(cd, "addresses[1]").getValue(valueExtractor));
        System.out.println(getElement(cd, "addresses[2]").getValue(valueExtractor));
    }

}
