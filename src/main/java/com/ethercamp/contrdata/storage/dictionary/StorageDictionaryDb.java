package com.ethercamp.contrdata.storage.dictionary;

import org.ethereum.datasource.DbSource;
import org.ethereum.datasource.XorDataSource;
import org.ethereum.util.ByteUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import static org.ethereum.crypto.HashUtil.sha3;

/**
 * DB managing the Layout => Contract => StorageDictionary mapping
 * <p>
 * Created by Anton Nashatyrev on 10.09.2015.
 */
@Service
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

    private DbSource db;

    @Autowired
    @Qualifier("storageDict")
    public void setDataSource(DbSource<byte[]> dataSource) {
        // TODO put cache
//        this.db = new ReadWriteCache(dataSource, WriteCache.CacheType.SIMPLE);
        this.db = dataSource;
    }

    public void flush() {
        db.flush();
    }

    public void close() {
        db.flush();
//        ((DbSource) db.getSource()).close();
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

