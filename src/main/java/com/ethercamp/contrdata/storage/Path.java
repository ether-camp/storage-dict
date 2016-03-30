package com.ethercamp.contrdata.storage;

import org.apache.commons.lang3.ArrayUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.*;

public class Path extends ArrayList<String> {

    private static final String SEPARATOR = "|";

    public Path(List<?> path) {
        super(path.stream().map(Object::toString).collect(toList()));
    }

    public String[] parts() {
        return toArray(ArrayUtils.EMPTY_STRING_ARRAY);
    }

    public Path extend(Object... extraParts) {
        return of(Stream.concat(stream(), Arrays.stream(extraParts)).toArray());
    }

    @Override
    public String toString() {
        return join(this, SEPARATOR);
    }

    public static Path empty() {
        return new Path(emptyList());
    }

    public static Path of(Object... parts) {
        return new Path(asList(parts));
    }

    public static Path parse(String path) {
        return of(split(defaultString(path), "\\" + SEPARATOR));
    }
}