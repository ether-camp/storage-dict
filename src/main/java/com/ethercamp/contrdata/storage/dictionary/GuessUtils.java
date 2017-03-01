package com.ethercamp.contrdata.storage.dictionary;

import org.spongycastle.util.encoders.Hex;

import java.math.BigInteger;

import static org.apache.commons.lang3.ArrayUtils.isEmpty;

public class GuessUtils {

    public static StorageDictionary.PathElement guessPathElement(byte[] key, byte[] storageKey) {
        if (isEmpty(key)) return null;

        StorageDictionary.PathElement el = null;

        Object value = guessValue(key);
        if (value instanceof String) {
            el = StorageDictionary.PathElement.createMapKey((String) value, storageKey);
        } else if (value instanceof BigInteger) {
            BigInteger bi = (BigInteger) value;
            if (bi.bitLength() < 32) {
                el = StorageDictionary.PathElement.createMapKey(bi.intValue(), storageKey);
            } else {
                el = StorageDictionary.PathElement.createMapKey("0x" + bi.toString(16), storageKey);
            }
        }

        return el;
    }

    public static Object guessValue(byte[] bytes) {
        int startZeroCnt = 0, startNonZeroCnt = 0;
        boolean asciiOnly = true;
        for (int i = 0; i < bytes.length; i++) {
            if (bytes[i] != 0) {
                if (startNonZeroCnt > 0 || i == 0) startNonZeroCnt++;
                else break;
            } else {
                if (startZeroCnt > 0 || i == 0) startZeroCnt++;
                else break;
            }
            asciiOnly &= bytes[i] > 0x1F && bytes[i] <= 0x7E;
        }

/*
        int endZeroCnt = 0, endNonZeroCnt = 0;
        for (int i = 0; i < bytes.length; i++) {
            if (bytes[bytes.length - i - 1] != 0) {
                if (endNonZeroCnt > 0 || i == 0) endNonZeroCnt++;
                else break;
            } else {
                if (endZeroCnt > 0 || i == 0) endZeroCnt++;
                else break;
            }
        }
*/

        if (startZeroCnt > 16) return new BigInteger(bytes);
        if (asciiOnly) return new String(bytes, 0, startNonZeroCnt);
        return Hex.toHexString(bytes);
    }
}
