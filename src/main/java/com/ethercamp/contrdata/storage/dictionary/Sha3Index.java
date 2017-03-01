package com.ethercamp.contrdata.storage.dictionary;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.util.FastByteComparisons;
import org.ethereum.vm.DataWord;
import org.spongycastle.util.encoders.Hex;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.Objects.isNull;
import static org.ethereum.crypto.HashUtil.sha3;

public class Sha3Index {

    @RequiredArgsConstructor
    public static class Entry {
        @Getter
        private final byte[] output;
        @Getter
        private final byte[] input;
        private int hashCode;

        @Override
        public int hashCode() {
            if (isNull(hashCode())) {
                this.hashCode = Arrays.hashCode(input);
            }
            return this.hashCode;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Entry) {
                return compareInput(((Entry) obj).getInput());
            }
            if (obj instanceof ByteArrayWrapper) {
                return compareInput(((ByteArrayWrapper) obj).getData());
            }
            return false;
        }

        private boolean compareInput(byte[] otherInput) {
            return FastByteComparisons.compareTo(
                    this.input, 0, this.input.length,
                    otherInput, 0, otherInput.length) == 0;
        }

        @Override
        public String toString() {
            return String.format("sha3('%s') = '%s'", Hex.toHexString(input), Hex.toHexString(output));
        }
    }

    private final static Sha3Index calculated = new Sha3Index() {{
        // Let's take 1000 as the max storage index
        IntStream.range(0, 1000).forEach(this::add);
    }};

    private final Map<Sha3Output, Entry> idx = new HashMap<>();

    protected void add(int i) {
        byte[] input = new DataWord(i).getData();
        Sha3Output output = Sha3Output.calc(input);
        idx.put(output, new Entry(output.getData(), input));
    }

    public boolean contains(byte[] decoded) {
        ByteArrayWrapper input = new ByteArrayWrapper(decoded);
        return Stream.concat(calculated.idx.values().stream(), idx.values().stream())
                .filter(entry -> entry.equals(input))
                .findFirst()
                .isPresent();

    }

    public void add(byte[] input) {
        if (contains(input)) return;

        Sha3Output output = Sha3Output.calc(input);
        idx.put(output, new Entry(output.getData(), input));
    }

    public Entry get(byte[] encoded) {
        Sha3Output output = Sha3Output.wrap(encoded);
        Entry entry = calculated.idx.get(output);
        if (isNull(entry)) {
            entry = idx.get(output);
        }

        return entry;
    }

    public void clear() {
        idx.clear();
    }

    public Collection<Entry> entries() {
        return idx.values();
    }

    public int size() {
        return idx.size();
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE, staticName = "wrap")
    private static class Sha3Output {

        private static final int COMPARISON_DATA_LEN = 20;

        @Getter
        private final byte[] data;
        private int hashCode;

        @Override
        public int hashCode() {
            if (isNull(hashCode)) {
                hashCode = Arrays.hashCode(Arrays.copyOf(data, COMPARISON_DATA_LEN));
            }
            return hashCode;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Sha3Output) {
                byte[] otherData = ((Sha3Output) obj).getData();
                return FastByteComparisons.compareTo(
                        data, 0, COMPARISON_DATA_LEN,
                        otherData, 0, COMPARISON_DATA_LEN) == 0;
            }
            return false;
        }

        @Override
        public String toString() {
            return Hex.toHexString(data);
        }


        static Sha3Output calc(byte[] input) {
            return wrap(sha3(input));
        }
    }
}
