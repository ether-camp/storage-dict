package com.ethercamp.contrdata.utils;

import org.ethereum.vm.DataWord;

import java.util.Random;

public class RandomUtils {

    public static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        new Random().nextBytes(bytes);
        return bytes;
    }

    public static DataWord randomDataWord() {
        return new DataWord(randomBytes(32));
    }
}
