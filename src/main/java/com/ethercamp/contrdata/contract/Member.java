package com.ethercamp.contrdata.contract;

import lombok.Getter;
import org.ethereum.vm.DataWord;

import static org.apache.commons.lang3.ArrayUtils.subarray;

@Getter
public class Member {

    private static final int SLOT_SIZE = 32;

    private final Member prev;
    private final int position;
    private final Ast.Type type;
    private final String name;
    private final boolean packed;

    private final ContractData contractData;
    private final int slotFreeSpace;

    public Member(Member prev, Ast.Variable variable, ContractData contractData) {
        this.contractData = contractData;
        this.name = variable.getName();
        this.type = variable.getType();
        this.prev = prev;

        int typeSize = size(getType());
        if (hasPrev()) {
            this.packed = getPrev().getSlotFreeSpace() >= typeSize;
            this.slotFreeSpace = (isPacked() ? getPrev().getSlotFreeSpace() : SLOT_SIZE) - typeSize;
            this.position = getPrev().getPosition() + 1;
        } else {
            this.packed = false;
            this.slotFreeSpace = SLOT_SIZE - typeSize;
            this.position = 0;
        }
    }

    public boolean hasPrev() {
        return prev != null;
    }

    public int reservedSlotsCount() {
        if (type.isStruct()) {
            return contractData.getStructFields(type.asStruct()).reservedSlotsCount();
        } else if (type.isStaticArray()) {
            int result = type.asArray().getSize();

            if (type.isStructArray()) {
                Ast.Type.Struct struct = type.asArray().getElementType().asStruct();
                result *= contractData.getStructFields(struct).reservedSlotsCount();
            }

            return result;
        }

        return isPacked() ? 0 : 1;
    }

    public int getStorageIndex() {
        int result = 0;
        if (hasPrev()) {
            result = getPrev().getStorageIndex() + (isPacked() ? 0 : getFirstPrevNonPacked().reservedSlotsCount());
        }
        return result;
    }

    private Member getFirstPrevNonPacked() {
        Member result = null;
        if (hasPrev()) {
            result = getPrev();
            while (result.isPacked()) {
                result = result.getPrev();
            }
        }
        return result;
    }

    private static int size(Ast.Type type) {
        int result = SLOT_SIZE;

        if (type.isEnum()) {
            result = 1;
        } else if (type.is("bool")) {
            result = 1;
        } else if (type.is("address")) {
            result = 20;
        }

        return result;
    }

    public DataWord extractValue(DataWord slot) {
        int size = size(getType());
        int from = getSlotFreeSpace();

        return new DataWord(subarray(slot.getData(), from, from + size));
    }
}