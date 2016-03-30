package com.ethercamp.contrdata.bc;

import org.ethereum.core.Block;
import org.ethereum.core.Repository;

/**
 * Created by Anton Nashatyrev on 24.03.2016.
 */
public interface LocalBlockchain extends EasyBlockchain {

    Block createBlock();

    Block createForkBlock(Block parent);

    Repository getBlockchainRepository();
}
