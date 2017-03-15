package com.ethercamp.contrdata.utils;

import com.ethercamp.contrdata.storage.Storage;
import com.ethercamp.contrdata.storage.dictionary.Layout;
import com.ethercamp.contrdata.storage.dictionary.StorageDictionary;
import com.ethercamp.contrdata.storage.dictionary.StorageDictionaryDb;
import org.ethereum.core.BlockchainImpl;
import org.ethereum.util.blockchain.LocalBlockchain;
import org.ethereum.util.blockchain.SolidityContract;

import static java.util.Objects.isNull;

public class BlockchainContractResource extends ContractResource {

    private final LocalBlockchain blockchain;
    private final StorageDictionaryDb dictionaryDb;
    private SolidityContract contract;

    public BlockchainContractResource(String name, LocalBlockchain blockchain, StorageDictionaryDb dictionaryDb) {
        super(name);
        this.blockchain = blockchain;
        this.dictionaryDb = dictionaryDb;
    }

    @Override
    protected Storage loadStorage() {
        return Storage.fromRepo(((BlockchainImpl) blockchain.getBlockchain()).getRepository());
    }

    @Override
    protected StorageDictionary loadStorageDictionary() {
        return dictionaryDb.getDictionaryFor(Layout.Lang.solidity, getContract().getAddress());
    }

    public BlockchainContractResource deploy(Object... args) {
        contract = blockchain.submitNewContract(getSource(), getName(), args);
        blockchain.createBlock();
        return this;
    }

    public SolidityContract getContract() {
        if (isNull(contract)) {
            throw new RuntimeException("Contract " + getName() + " is not deployed yet.");
        }
        return contract;
    }
}
