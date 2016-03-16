package com.ethercamp.storagedict;

import com.ethercamp.storagedict.concept.Path;
import lombok.Data;
import lombok.NoArgsConstructor;

import static org.ethereum.util.ByteUtil.toHexString;

@Data
@NoArgsConstructor
public class StorageKey {

    private static final String PATH_SEPARATOR = "|";

    private String type;
    private String path;
    private String name;
    private String raw;

    private boolean decoded;

    public StorageKey(StorageDictionary.PathElement element) {
        this.type = element.type.name();
        this.path = new Path(element.getFullPath()).toString();
        this.name = element.key;
        this.raw = toHexString(element.getHash());
    }
}