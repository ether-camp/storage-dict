package com.ethercamp.contrdata.blockchain;

import org.ethereum.vm.DataWord;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Represents the contract storage which is effectively the
 * mapping( uint256 => uint256 )
 *
 * Created by Anton Nashatyrev on 23.03.2016.
 */
public interface ContractStorage {

    int size();

    Set<DataWord> keySet();

    DataWord get(DataWord key);

    Map<DataWord, DataWord> get(List<DataWord> keys);
}
