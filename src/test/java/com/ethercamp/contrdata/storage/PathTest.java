package com.ethercamp.contrdata.storage;

import com.ethercamp.contrdata.BaseTest;
import com.ethercamp.contrdata.contract.Ast;
import com.ethercamp.contrdata.contract.ContractData;
import com.ethercamp.contrdata.storage.dictionary.Layout;
import com.ethercamp.contrdata.storage.dictionary.StorageDictionary;
import org.ethereum.util.blockchain.SolidityContract;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.Arrays;

import static java.util.stream.Collectors.joining;
import static org.junit.Assert.assertEquals;

public class PathTest extends BaseTest {

    private String source;

    @Value("${classpath:contracts/ProfilesPathTest.sol}")
    public void setSource(Resource source) throws IOException {
        this.source = resourceToString(source);
    }

    @Test
    public void testHumanReadableParsing() throws IOException {
        SolidityContract contract = blockchain.submitNewContract(source);
        ContractData contractData = getContractData(contract, source, "ProfilesPathTest");

        assertParsedEquals("0|0000000000000000000000005db10750e8caff27f906b41c71b3471057dd2004|3", "profileByAddress[0000000000000000000000005db10750e8caff27f906b41c71b3471057dd2004].resident", contractData);
        assertParsedEquals("4|0000000000000000000000005db10750e8caff27f906b41c71b3471057dd2004|125|4|11", "register[0000000000000000000000005db10750e8caff27f906b41c71b3471057dd2004][125].friends[11]", contractData);
        assertParsedEquals("3|0000000000000000000000005db10750e8caff27f906b41c71b3471057dd2004", "counterByAddress[0000000000000000000000005db10750e8caff27f906b41c71b3471057dd2004]", contractData);
        assertParsedEquals("2|4|12|0", "profile.friends[12].alias", contractData);
        assertParsedEquals("1|10|4|12|1", "profiles[10].friends[12].age", contractData);
        assertParsedEquals("1|10|1", "profiles[10].age", contractData);
    }

    @Test
    public void testPartition() {
        Object[] parts = {0, 1, 2, 3, 4, 5};

        Path path = Path.of(parts);
        assertEquals(parts.length, path.size());
        assertEquals(parts.length - 1, path.tail().size());
        assertEquals(String.valueOf(parts[0]), path.first());
        assertEquals(Arrays.stream(parts).map(Object::toString).collect(joining("|")), path.toString());

        Path empty = Path.empty();
        Assert.assertTrue(empty.isEmpty());
        Assert.assertNull(empty.first());

        Path tail = empty.tail();
        Assert.assertNotNull(tail);
        Assert.assertTrue(tail.isEmpty());

        Path extended = path.extend(parts);
        Assert.assertNotNull(extended);
        assertEquals(parts.length * 2, extended.size());
    }

    protected ContractData getContractData(SolidityContract contract, String source, String contractName) throws IOException {
        Ast.Contract ast = getContractAllDataMembers(source, contractName);
        StorageDictionary dictionary = dictDb.getDictionaryFor(Layout.Lang.solidity, contract.getAddress());

        return new ContractData(ast, dictionary);
    }

    private static void assertParsedEquals(String expected, String humanReadable, ContractData contractData) {
        Path parsed = Path.parseHumanReadable(humanReadable, contractData);
        String actual = parsed.toString();

        System.out.printf("'%s' -> '%s'\n", humanReadable, actual);

        assertEquals(expected, actual);
    }
}