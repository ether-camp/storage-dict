package com.ethercamp.contrdata.storage.dictionary;

import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.vm.DataWord;
import org.junit.Test;

import java.util.Arrays;
import java.util.stream.IntStream;

import static com.ethercamp.contrdata.utils.RandomUtils.randomBytes;
import static org.ethereum.crypto.HashUtil.sha3;
import static org.junit.Assert.*;

public class Sha3IndexTest {


    private static void assertAdded(byte[] input, Sha3Index index) {
        byte[] output = sha3(input);

        Sha3Index.Entry entry = index.get(output);
        assertNotNull(entry);
        assertTrue(entry.equals(new ByteArrayWrapper(input)));
        assertTrue(Arrays.equals(input, entry.getInput()));
        assertTrue(Arrays.equals(output, entry.getOutput()));
    }

    @Test
    public void testAdding() {
        Sha3Index index = new Sha3Index();

        assertEquals(0, index.size());

        IntStream.range(32, 64).forEach(len -> {
            byte[] decoded = randomBytes(len);
            index.add(decoded);

            assertAdded(decoded, index);
        });

        assertTrue(index.size() > 0);
    }

    @Test
    public void testPrecalculatedHahses() {
        Sha3Index index = new Sha3Index();

        assertEquals(0, index.size());

        IntStream.range(0, 1000).forEach(i -> {
            byte[] decoded = new DataWord(i).getData();
            index.add(decoded);

            assertAdded(decoded, index);
        });

        assertEquals(0, index.size());
    }
}