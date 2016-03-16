package com.ethercamp.storagedict;

import org.ethereum.datasource.CachingDataSource;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.datasource.LevelDbDataSource;
import org.ethereum.datasource.XorDataSource;
import org.ethereum.util.ByteUtil;

import static org.ethereum.crypto.SHA3Helper.sha3;

/**
 * DB managing the Layout => Contract => StorageDictionary mapping
 * <p>
 * Created by Anton Nashatyrev on 10.09.2015.
 */
public class StorageDictionaryDb {

    public enum Layout {
        Solidity("solidity"),
        Serpent("serpent");

        private final byte[] fingerprint;

        Layout(String name) {
            this.fingerprint = sha3(name.getBytes());
        }

        public byte[] getFingerprint() {
            return fingerprint;
        }
    }

    public static StorageDictionaryDb INST = new StorageDictionaryDb();

    private CachingDataSource db;

    private StorageDictionaryDb() {
        KeyValueDataSource db = new LevelDbDataSource("storageDict");
        db.init();

        this.db = new CachingDataSource(db);
    }

    public void flush() {
        db.flush();
    }

    public void close() {
        db.flush();
        db.close();
    }

    public StorageDictionary get(Layout layout, byte[] contractAddress) {
        StorageDictionary storageDictionary = getOrCreate(layout, contractAddress);
        return storageDictionary.isExist() ? storageDictionary : null;
    }

    public StorageDictionary getOrCreate(Layout layout, byte[] contractAddress) {
        byte[] key = ByteUtil.xorAlignRight(layout.getFingerprint(), contractAddress);
        XorDataSource dataSource = new XorDataSource(db, key);
        return new StorageDictionary(dataSource);
    }
}

