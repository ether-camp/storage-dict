package com.ethercamp.storagedict.concept;

import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.max;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.apache.commons.lang3.StringUtils.*;

public class Path extends ArrayList<String> {

    private static final String SEPARATOR = "|";
    public static final Path EMPTY = new Path(emptyList());

    public Path(List<String> path) {
        super(path);
    }

    public Path(String... parts) {
        this(asList(parts));
    }

    public static Path parse(String path) {
        return new Path(split(defaultString(path), "\\" + SEPARATOR));
    }

    public Path initial() {
        List<String> parts = subList(0, max(0, size() - 1));
        return new Path(parts);
    }

    public String lastPart() {
        return isEmpty() ? null : get(size() - 1);
    }

    public String[] parts() {
        return toArray(new String[]{});
    }

    public Path extend(String... extraParts) {
        Path extended = new Path(this);
        extended.addAll(asList(extraParts));
        return extended;
    }

    @Override
    public String toString() {
        return join(this, SEPARATOR);
    }
}