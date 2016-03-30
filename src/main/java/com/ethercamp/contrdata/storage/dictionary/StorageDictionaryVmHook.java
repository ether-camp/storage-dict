package com.ethercamp.contrdata.storage.dictionary;

import lombok.extern.slf4j.Slf4j;
import org.ethereum.vm.DataWord;
import org.ethereum.vm.OpCode;
import org.ethereum.vm.VM;
import org.ethereum.vm.VMHook;
import org.ethereum.vm.program.Program;
import org.ethereum.vm.program.Stack;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

import static org.ethereum.crypto.HashUtil.sha3;

@Slf4j
@Component
public class StorageDictionaryVmHook implements VMHook {

    @Autowired
    private ApplicationContext applicationContext;

    @PostConstruct
    public void init() {
        VM.setVmHook(this);
    }

    private StorageDictionaryHandler newHandler(Program program) {
        return applicationContext.getBean(StorageDictionaryHandler.class, program.getOwnerAddress());
    }

    java.util.Stack<StorageDictionaryHandler> handlerStack = new java.util.Stack<>();

    @Override
    public void startPlay(Program program) {
        try {
//                System.out.println("Start play: " + program.getOwnerAddress());
            handlerStack.push(newHandler(program)).vmStartPlayNotify();
        } catch (Exception e) {
            log.error("Error within handler: ", e);
        }
    }

    @Override
    public void stopPlay(Program program) {
        try {
//                System.out.println("Stop play: " + handler);
            byte[] address = program.getOwnerAddress().getLast20Bytes();
            handlerStack.pop().vmEndPlayNotify(program.getStorage().getContractDetails(address));
        } catch (Exception e) {
            log.error("Error within handler: ", e);
        }
    }

    @Override
    public void step(Program program, OpCode opcode) {
        try {
            Stack stack = program.getStack();
            switch (opcode) {
                case SSTORE:
                    DataWord addr = stack.get(stack.size() - 1);
                    DataWord value = stack.get(stack.size() - 2);
                    handlerStack.peek().vmSStoreNotify(addr, value);
                    break;
                case SHA3:
                    DataWord memOffsetData = stack.get(stack.size() - 1);
                    DataWord lengthData = stack.get(stack.size() - 2);
                    byte[] buffer = program.memoryChunk(memOffsetData.intValue(), lengthData.intValue());
                    byte[] encoded = sha3(buffer);
                    DataWord word = new DataWord(encoded);
                    handlerStack.peek().vmSha3Notify(buffer, word);
                    break;
            }
        } catch (Exception e) {
            log.error("Error within handler: ", e);
        }
    }
}
