package com.ethercamp.contrdata.contract;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

public class Members extends ArrayList<Member> {

    public Members() {
        super();
    }

    public Members(List<Member> members) {
        super(members);
    }

    private Member findBy(Predicate<Member> predicate) {
        return stream()
                .filter(predicate)
                .findFirst()
                .orElse(null);
    }

    public Member findByName(String name) {
        return findBy(member -> StringUtils.equals(member.getName(), name));
    }

    public Member findByPosition(int position) {
        return findBy(member -> member.getPosition() == position);
    }

    public int reservedSlotsCount() {
        return stream().mapToInt(Member::reservedSlotsCount).sum();
    }

    public Members filter(Predicate<? super Member> predicate) {
        return new Members(stream().filter(predicate).collect(toList()));
    }

    public Members page(int page, int size) {
        int offset = page * size;
        int fromIndex = max(offset, 0);
        int toIndex = min(size(), offset + size);

        return new Members((fromIndex < toIndex) ? subList(fromIndex, toIndex) : emptyList());
    }

    private static Members of(ContractData contractData, Ast.Entries<Ast.Variable> variables) {
        final Members members = new Members();
        variables.stream().forEach(var -> {
            Member last = members.isEmpty() ? null : members.get(members.size() - 1);
            members.add(new Member(last, var, contractData));
        });

        return members;
    }

    public static Members ofContract(ContractData contractData) {
        return of(contractData, contractData.getContract().getVariables());
    }

    public static Members ofStructure(ContractData contractData, Ast.Structure structure) {
        return of(contractData, structure.getVariables());
    }
}