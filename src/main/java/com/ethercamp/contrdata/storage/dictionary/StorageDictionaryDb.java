package com.ethercamp.contrdata.storage.dictionary;

import org.ethereum.datasource.DbSource;
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

    private DbSource db;

    @Autowired
    public StorageDictionaryDb(@Qualifier("storageDict") DbSource<byte[]> dataSource) {
        // TODO put cache
//        this.db = new ReadWriteCache(dataSource, WriteCache.CacheType.SIMPLE);
        this.db = dataSource;
    }

    @Override
    public void flush() {
        db.flush();
    }

    @PreDestroy
    @Override
    public void close() {
        // TODO put close
        db.flush();
//        ((DbSource) db.getSource()).close();
    }

    public StorageDictionary getDictionaryFor(Layout.Lang lang, byte[] contractAddress) {
        byte[] key = ByteUtil.xorAlignRight(lang.getFingerprint(), contractAddress);
        XorDataSource dataSource = new XorDataSource(db, key);

        return new StorageDictionary(dataSource);
    }
}

