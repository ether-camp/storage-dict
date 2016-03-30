package com.ethercamp.contrdata.storage;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class StoragePage {

    private List<StorageEntry> entries;
    private int number;
    private int size;
    private int total;
}
