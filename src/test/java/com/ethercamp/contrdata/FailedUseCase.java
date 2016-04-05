package com.ethercamp.contrdata;

import com.ethercamp.contrdata.contract.Ast;
import com.ethercamp.contrdata.storage.Path;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;
import org.ethereum.vm.DataWord;

import java.util.ArrayList;
import java.util.Map;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class FailedUseCase {

    private String address;
    private Path path;
    private Ast.Contract dataMembers;
    private Map<String, String> storageDictionary;
    private Map<DataWord, DataWord> storage;

    public static class List extends ArrayList<FailedUseCase> {}
}
