package com.ethercamp.contrdata.utils;

import com.ethercamp.contrdata.contract.Ast;
import com.ethercamp.contrdata.contract.ContractData;
import com.ethercamp.contrdata.storage.Path;
import com.ethercamp.contrdata.storage.Storage;
import com.ethercamp.contrdata.storage.dictionary.StorageDictionary;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.ethereum.solidity.compiler.SolidityCompiler;
import org.ethereum.vm.DataWord;
import org.spongycastle.util.encoders.Hex;

import java.io.IOException;
import java.util.function.Function;

import static org.junit.Assert.assertFalse;

@RequiredArgsConstructor
public abstract class ContractResource {

    @Getter
    private final String name;
    @Getter
    private String address = "00";
    @Getter(lazy = true)
    private final String source = loadSource();
    @Getter(lazy = true)
    private final Ast.Contract contractAst = createContractAst();
    @Getter(lazy = true)
    private final Storage storage = loadStorage();
    @Getter(lazy = true)
    private final StorageDictionary storageDictionary = loadStorageDictionary();
    @Getter(lazy = true)
    private final ContractData contractData = createContractData();

    protected ContractResourceLoader loader = new ContractResourceLoader();

    protected String loadSource() {
        return loader.loadAsString("real", name, "contract.sol");
    }

    protected Ast.Contract createContractAst() {
        try {
            SolidityCompiler.Result compilationResult = SolidityCompiler.compile(getSource().getBytes(), false, SolidityCompiler.Options.AST);
            assertFalse(compilationResult.errors, compilationResult.isFailed());
            return Ast.parse(compilationResult.output).getContractAllDataMembers(name);
        } catch (IOException e) {
            throw new RuntimeException("Cannot compile " + name + " contract: ", e);
        }
    }

    protected abstract Storage loadStorage();

    protected abstract StorageDictionary loadStorageDictionary();

    protected ContractData createContractData() {
        return new ContractData(getContractAst(), getStorageDictionary());
    }

    public Function<DataWord, DataWord> getValueExtractor() {
        return key -> getStorage().get(Hex.decode(getAddress()), key);
    }

    public String getMemberValue(String humanReadablePath) {
        Path path = Path.parseHumanReadable(humanReadablePath, getContractData());
        ContractData.Element element = getContractData().elementByPath(path.parts());
        String value = element.getValue(getValueExtractor());

        System.out.printf("%s.%s = '%s'\n", getName(), humanReadablePath, value);

        return value;
    }
}
