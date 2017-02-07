package com.ethercamp.contrdata.storage.dictionary;

import org.ethereum.datasource.CachingDataSource;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.datasource.XorDataSource;
import org.ethereum.util.ByteUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.io.Closeable;
import java.io.Flushable;

/**
 * DB managing the Layout => Contract => StorageDictionary mapping
 * <p>
 * Created by Anton Nashatyrev on 10.09.2015.
 */
@Service
public class StorageDictionaryDb implements Flushable, Closeable {

    private CachingDataSource db;

    @Autowired
    public StorageDictionaryDb(@Qualifier("storageDict") KeyValueDataSource dataSource) {
        this.db = new CachingDataSource(dataSource);
    }

    @Override
    public void flush() {
        db.flush();
    }

    @PreDestroy
    @Override
    public void close() {
        db.flush();
        db.close();
    }

    public StorageDictionary getDictionaryFor(Layout.Lang lang, byte[] contractAddress) {
        byte[] key = ByteUtil.xorAlignRight(lang.getFingerprint(), contractAddress);
        XorDataSource dataSource = new XorDataSource(db, key);

        return new StorageDictionary(dataSource);
    }
}

