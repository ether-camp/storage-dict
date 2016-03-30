package com.ethercamp.contrdata.storage;

import org.ethereum.vm.DataWord;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface Storage {

    int size(byte[] address);

    Map<DataWord, DataWord> entries(byte[] address, List<DataWord> keys);

    Set<DataWord> keys(byte[] address);

    DataWord get(byte[] address, DataWord key);
}
