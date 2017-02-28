package com.ethercamp.contrdata.contract;

import lombok.Getter;
import org.ethereum.vm.DataWord;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.ArrayUtils.subarray;
import static org.apache.commons.lang3.math.NumberUtils.toInt;

@Getter
public class Member {

    public static final int BYTES_IN_SLOT = 32;
    private static final int BITS_IN_BYTE = 8;

    private static final Pattern BYTES_TYPE_PATTERN = Pattern.compile("^bytes(\\d{0,2})$");
    private static final Pattern INT_TYPE_PATTERN = Pattern.compile("^u?int(\\d{0,3})$");


    private static final int BITS_IN_SLOT = BITS_IN_BYTE * BYTES_IN_SLOT;
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
            this.slotFreeSpace = (isPacked() ? getPrev().getSlotFreeSpace() : BYTES_IN_SLOT) - typeSize;
            this.position = getPrev().getPosition() + 1;
        } else {
            this.packed = false;
            this.slotFreeSpace = BYTES_IN_SLOT - typeSize;
            this.position = 0;
        }
    }

    public boolean hasPrev() {
        return nonNull(prev);
    }

    public int reservedSlotsCount() {
        if (type.isStruct()) {
            return contractData.getStructFields(type.asStruct()).reservedSlotsCount();
        } else if (type.isStaticArray()) {
            int arrSize = type.asArray().getSize();

            Ast.Type nestedType = type.asArray().getElementType();
            if (nestedType.isStruct()) {
                Ast.Type.Struct struct = type.asArray().getElementType().asStruct();
                return arrSize * contractData.getStructFields(struct).reservedSlotsCount();
            }

            return (int) Math.ceil((float) arrSize * size(nestedType) / BYTES_IN_SLOT);
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

    public DataWord extractValue(DataWord slot) {
        if (slot == null) return null;

        int size = size(getType());
        int from = getSlotFreeSpace();

        return new DataWord(subarray(slot.getData(), from, from + size));
    }

    static int size(Ast.Type type) {
        int result = BYTES_IN_SLOT;

        if (type.isEnum()) {
            result = 1;
        } else if (type.isElementary()) {
            if (type.is("bool")) {
                result = 1;
            } else if (type.is("address")) {
                result = 20;
            } else if (type.is(name -> name.startsWith("bytes"))) {
                result = size(type, BYTES_TYPE_PATTERN, BYTES_IN_SLOT);
            } else if (type.is(name -> name.contains("int"))) {
                result = size(type, INT_TYPE_PATTERN, BITS_IN_SLOT) / BITS_IN_BYTE;
            }
        }

        return result;
    }

    private static int size(Ast.Type type, Pattern pattern, int defaultSize) {
        int result = defaultSize;
        Matcher matcher = pattern.matcher(type.getName());
        if (matcher.matches()) {
            result = toInt(matcher.group(1), defaultSize);
        }
        return result;
    }
}