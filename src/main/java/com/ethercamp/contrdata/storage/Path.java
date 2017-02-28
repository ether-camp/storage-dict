package com.ethercamp.contrdata.storage;

import com.ethercamp.contrdata.contract.Ast;
import com.ethercamp.contrdata.contract.ContractData;
import com.ethercamp.contrdata.contract.Member;
import com.ethercamp.contrdata.contract.Members;
import org.apache.commons.lang3.ArrayUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Objects.isNull;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.*;

public class Path extends ArrayList<String> {

    private static final String SEPARATOR = "|";

    public Path() {
        this(new ArrayList<>());
    }

    public Path(List<?> path) {
        super(path.stream().map(Object::toString).collect(toList()));
    }

    public String[] parts() {
        return toArray(ArrayUtils.EMPTY_STRING_ARRAY);
    }

    public Path extend(Object... extraParts) {
        return of(Stream.concat(stream(), Arrays.stream(extraParts)).toArray());
    }

    public String first() {
        return isEmpty() ? null : get(0);
    }

    public String last() {
        return isEmpty() ? null : get(size() - 1);
    }

    public String removeLast() {
        if (isEmpty()) {
            throw new IllegalStateException("Can not remove last item: path is empty.");
        }
        return remove(size() - 1);
    }

    public Path tail() {
        return (size() > 1) ? new Path(subList(1, size())) : empty();
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

    public static Path parseHumanReadable(String path, ContractData contractData) {
        Path humanReadable = of(path.split("(\\[|(\\]\\[)|(\\]\\.?))|\\."));
        return fromHumanReadable(humanReadable, null, contractData.getMembers(), contractData, new Path());
    }

    private static Path fromHumanReadable(Path humanReadable, Ast.Type type, Members members, ContractData contractData, Path result) {
        String part = humanReadable.first();
        if (isNull(part)) {
            return result;
        }

        if (isNull(type)) {
            Member member = members.findByName(part);
            part = String.valueOf(member.getPosition());
            type = member.getType();
        } else if (type.isMapping()) {
            type = type.asMapping().getValueType();
        } else if (type.isArray()) {
            type = type.asArray().getElementType();
        }

        if (type.isStruct()) {
            Ast.Type.Struct struct = type.asStruct();
            members = contractData.getStructFields(struct);
            type = null;
        }

        return fromHumanReadable(humanReadable.tail(), type, members, contractData, result.extend(part));
    }

}