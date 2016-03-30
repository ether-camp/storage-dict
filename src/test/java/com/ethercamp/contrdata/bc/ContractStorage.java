package com.ethercamp.contrdata.bc;

import org.ethereum.vm.DataWord;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by eshevchenko on 28.03.16.
 */
public interface ContractStorage {

    int size();

    Set<DataWord> keySet();

    DataWord get(DataWord key);

    Map<DataWord, DataWord> get(List<DataWord> keys);
}
