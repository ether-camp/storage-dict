package com.ethercamp.contrdata.storage.dictionary;

import lombok.Getter;
import org.ethereum.util.Utils;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.util.Arrays;

import static com.ethercamp.contrdata.storage.dictionary.GuessUtils.guessPathElement;
import static com.ethercamp.contrdata.storage.dictionary.StorageDictionary.PathElement.toVirtualStorageKey;
import static com.ethercamp.contrdata.storage.dictionary.StorageDictionary.emptyPathElements;
import static com.ethercamp.contrdata.storage.dictionary.StorageDictionary.pathElements;
import static java.util.Objects.isNull;
import static org.ethereum.crypto.HashUtil.sha3;

public interface Layout {

    enum Lang {
        solidity,
        serpent;

        @Getter(lazy = true)
        private final byte[] fingerprint = fingerprint();

        private byte[] fingerprint() {
            return sha3(name().getBytes());
        }
    }

    interface DictPathResolver {

        Lang getLang();

        StorageDictionary.PathElement[] resolvePath(byte[] key, Sha3Index index);
    }

    //    @Component
    class SerpentDictPathResolver implements DictPathResolver {

        @Override
        public Lang getLang() {
            return Lang.serpent;
        }

        @Override
        public StorageDictionary.PathElement[] resolvePath(byte[] key, Sha3Index index) {
            Sha3Index.Entry entry = index.get(key);
            if (entry != null) {
                if (entry.getInput().length > 32 && entry.getInput().length % 32 == 0 &&
                        Arrays.equals(key, entry.getOutput())) {

                    int pathLength = entry.getInput().length / 32;
                    StorageDictionary.PathElement[] ret = new StorageDictionary.PathElement[pathLength];
                    for (int i = 0; i < ret.length; i++) {
                        byte[] storageKey = sha3(entry.getInput(), 0, (i + 1) * 32);
                        ret[i] = guessPathElement(Arrays.copyOfRange(entry.getInput(), i * 32, (i + 1) * 32), storageKey);
                        ret[i].type = StorageDictionary.PathElement.Type.MapKey;
                    }
                    return ret;
                } else {
                    // not a Serenity contract
                }
            }
            StorageDictionary.PathElement storageIndex = guessPathElement(key, key);
            storageIndex.type = StorageDictionary.PathElement.Type.StorageIndex;
            return pathElements(storageIndex);
        }
    }

    @Component
    class SolidityDictPathResolver implements DictPathResolver {

        @Override
        public Lang getLang() {
            return Lang.solidity;
        }

        @Override
        public StorageDictionary.PathElement[] resolvePath(byte[] key, Sha3Index index) {
            Sha3Index.Entry sha3 = index.get(key);

            if (isNull(sha3)) {
                StorageDictionary.PathElement pathElement = guessPathElement(key, key);
                pathElement.type = StorageDictionary.PathElement.Type.StorageIndex;
                pathElement.storageKey = key;

                return pathElements(pathElement);
            } else {
                byte[] subKey = Arrays.copyOfRange(sha3.getInput(), 0, sha3.getInput().length - 32);
                byte[] nxtKey = Arrays.copyOfRange(sha3.getInput(), sha3.getInput().length - 32, sha3.getInput().length);

                // hashKey = key - 1
                StorageDictionary.PathElement containerKey = guessPathElement(subKey, toVirtualStorageKey(sha3.getOutput()));
                // hashKey = key & subkey.length == 0 for dyn arrays
                StorageDictionary.PathElement.Type type = subKey.length == 0 ? StorageDictionary.PathElement.Type.ArrayIndex : StorageDictionary.PathElement.Type.Offset;
                int offset = new BigInteger(key).subtract(new BigInteger(sha3.getOutput())).intValue();
                StorageDictionary.PathElement containerValKey = new StorageDictionary.PathElement(type, offset, key);

                return Utils.mergeArrays(
                        resolvePath(nxtKey, index),
                        pathElements(isNull(containerKey) ? emptyPathElements() : pathElements(containerKey)),
                        pathElements(containerValKey));
            }
        }
    }
}
