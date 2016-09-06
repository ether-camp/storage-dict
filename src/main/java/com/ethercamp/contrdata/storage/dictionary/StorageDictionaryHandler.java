package com.ethercamp.contrdata.storage.dictionary;

import lombok.extern.slf4j.Slf4j;
import org.ethereum.config.SystemProperties;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.db.ContractDetails;
import org.ethereum.util.Utils;
import org.ethereum.vm.DataWord;
import org.spongycastle.util.encoders.Hex;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.apache.commons.lang3.ArrayUtils.isEmpty;
import static org.ethereum.crypto.HashUtil.sha3;

@Slf4j(topic = "VM")
@Component
@Scope("prototype")
public class StorageDictionaryHandler {

    @Autowired
    private StorageDictionaryDb dictionaryDb;

    private static class Entry {
        final DataWord hashValue;
        final byte[] input;

        public Entry(DataWord hashValue, byte[] input) {
            this.hashValue = hashValue.clone();
            this.input = input;
        }

        @Override
        public String toString() {
            return "sha3(" + Hex.toHexString(input) + ") = " + hashValue;
        }
    }

    // hashes for storage indexes can be pre-calculated by the Solidity compiler
    private static final Map<ByteArrayWrapper, Entry> ConstHashes = new HashMap<ByteArrayWrapper, Entry>() {{

        DataWord ONE = new DataWord(1);
        DataWord storageIdx = new DataWord();
        // Let's take 5000 as the max storage index
        for (int i = 0; i < 5000; i++, storageIdx.add(ONE)) {
            byte[] sha3 = sha3(storageIdx.getData());
            put(toMapKey(sha3), new StorageDictionaryHandler.Entry(new DataWord(sha3), storageIdx.clone().getData()));
        }
    }};

    private byte[] contractAddress;

    private Map<ByteArrayWrapper, Entry> hashes = new HashMap<>();
    private Map<ByteArrayWrapper, DataWord> storeKeys = new HashMap<>();

    public StorageDictionaryHandler(DataWord ownerAddress) {
        try {
            contractAddress = ownerAddress.getLast20Bytes();
        } catch (Throwable e) {
            log.error("Unexpected exception: ", e);
            // ignore exception to not halt VM execution
        }
    }

    static ByteArrayWrapper toMapKey(byte[] hash) {
        return new ByteArrayWrapper(Arrays.copyOfRange(hash, 0, 20));
    }

    public void vmSha3Notify(byte[] in, DataWord out) {
        try {
            hashes.put(toMapKey(out.getData()), new Entry(out.clone(), in));
        } catch (Throwable e) {
            log.error("Unexpected exception: ", e);
            // ignore exception to not halt VM execution
        }
    }

    public void vmSStoreNotify(DataWord key, DataWord value) {
        try {
            storeKeys.put(new ByteArrayWrapper(key.clone().getData()), value.clone());
        } catch (Throwable e) {
            log.error("Unexpected exception: ", e);
            // ignore exception to not halt VM execution
        }
    }

    private Entry findHash(byte[] key) {
        ByteArrayWrapper mapKey = toMapKey(key);
        Entry entry = hashes.get(mapKey);
        if (entry == null) {
            entry = ConstHashes.get(mapKey);
        }
        return entry;
    }

    public StorageDictionary.PathElement[] getKeyOriginSerpent(byte[] key) {
        Entry entry = findHash(key);
        if (entry != null) {
            if (entry.input.length > 32 && entry.input.length % 32 == 0 &&
                    Arrays.equals(key, entry.hashValue.getData())) {

                int pathLength = entry.input.length / 32;
                StorageDictionary.PathElement[] ret = new StorageDictionary.PathElement[pathLength];
                for (int i = 0; i < ret.length; i++) {
                    byte[] storageKey = sha3(entry.input, 0, (i + 1) * 32);
                    ret[i] = guessPathElement(Arrays.copyOfRange(entry.input, i * 32, (i + 1) * 32), storageKey)[0];
                    ret[i].type = StorageDictionary.PathElement.Type.MapKey;
                }
                return ret;
            } else {
                // not a Serenity contract
            }
        }
        StorageDictionary.PathElement[] storageIndex = guessPathElement(key, key);
        storageIndex[0].type = StorageDictionary.PathElement.Type.StorageIndex;
        return storageIndex;
    }

    public StorageDictionary.PathElement[] getKeyOriginSolidity(byte[] key) {
        Entry entry = findHash(key);
        if (entry == null) {
            StorageDictionary.PathElement[] storageIndex = guessPathElement(key, key);
            storageIndex[0].type = StorageDictionary.PathElement.Type.StorageIndex;
            storageIndex[0].storageKey = key;
            return storageIndex;
        } else {
            byte[] subKey = Arrays.copyOfRange(entry.input, 0, entry.input.length - 32); // subkey.length == 0 for dyn arrays
            long offset = new BigInteger(key).subtract(new BigInteger(entry.hashValue.clone().getData())).longValue();
            return Utils.mergeArrays(
                    getKeyOriginSolidity(Arrays.copyOfRange(entry.input, entry.input.length - 32, entry.input.length)),
                    guessPathElement(subKey, StorageDictionary.PathElement.getVirtualStorageKey(entry.hashValue.getData())), // hashKey = key - 1
                    StorageDictionary.pathElements(new StorageDictionary.PathElement // hashKey = key
                            (subKey.length == 0 ? StorageDictionary.PathElement.Type.ArrayIndex : StorageDictionary.PathElement.Type.Offset, (int) offset, key)));
        }
    }

    public StorageDictionary.PathElement[] guessPathElement(byte[] key, byte[] storageKey) {
        if (isEmpty(key)) return StorageDictionary.emptyPathElements();

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
        return StorageDictionary.pathElements(el);
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
        if (startZeroCnt > 16) return new BigInteger(bytes);
        if (asciiOnly) return new String(bytes, 0, startNonZeroCnt);
        return Hex.toHexString(bytes);
    }

    public void dumpKeys(final ContractDetails storage) {
        StorageDictionary solidityDict = getDictionary(StorageDictionaryDb.Layout.Solidity);
        StorageDictionary serpentDict = getDictionary(StorageDictionaryDb.Layout.Serpent);

        for (ByteArrayWrapper key : storeKeys.keySet()) {
            solidityDict.addPath(getKeyOriginSolidity(key.getData()));
            serpentDict.addPath(getKeyOriginSerpent(key.getData()));
        }

        if (SystemProperties.getDefault().getConfig().hasPath("vm.structured.storage.dictionary.dump")) {

            String contractAddress = Hex.toHexString(this.contractAddress);
            File dumpDir = new File("json");
            dumpDir.mkdirs();

            // for debug purposes only
            if (solidityDict.hasChanges()) {
                File f = new File(dumpDir, contractAddress + ".sol.txt");
                writeDump(storage, solidityDict, f);
            }

            if (serpentDict.hasChanges()) {
                File f = new File(dumpDir, contractAddress + ".se.txt");
                writeDump(storage, serpentDict, f);
            }

            File f = new File(dumpDir, contractAddress + ".hash.txt");
            try (BufferedWriter w = new BufferedWriter(new FileWriter(f, true))) {

                w.write("\nHashes:\n");
                for (Entry entry : hashes.values()) {
                    w.write(entry + "\n");
                }

                w.write("\nSSTORE:\n");
                for (Map.Entry<ByteArrayWrapper, DataWord> entry : storeKeys.entrySet()) {
                    w.write(entry + "\n");
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        solidityDict.store();
        serpentDict.store();

        dictionaryDb.flush();
    }

    private StorageDictionary getDictionary(StorageDictionaryDb.Layout layout) {
        return dictionaryDb.getOrCreate(layout, contractAddress);
    }

    private static void writeDump(ContractDetails storage, StorageDictionary serpentDict, File f) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(f))) {
            writer.write(serpentDict.dump(storage));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void vmStartPlayNotify() {
    }

    public void vmEndPlayNotify(ContractDetails contractDetails) {
        try {
            dumpKeys(contractDetails);
        } catch (Throwable e) {
            log.error("Unexpected exception: ", e);
            // ignore exception to not halt VM execution
        }
    }
}

