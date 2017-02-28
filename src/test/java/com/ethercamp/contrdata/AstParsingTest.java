package com.ethercamp.contrdata;

import com.ethercamp.contrdata.contract.Ast;
import com.ethercamp.contrdata.utils.RealContractResource;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;

import java.io.IOException;

import static org.junit.Assert.assertNotNull;

public class AstParsingTest extends BaseTest{

    @Value("${classpath:contracts/real/YoutubeViews.sol}")
    private Resource youtubeViewsSource;

    @Test
    public void youtubeViews() throws IOException {
        String source = resourceToString(youtubeViewsSource);

        Ast.Contract dataMembers = getContractAllDataMembers(source, "YoutubeViews");
        assertNotNull(dataMembers);
    }

    @Test
    public void roulethAstParsingTest() {
        RealContractResource cr = new RealContractResource("Rouleth", "");
        Ast.Contract ast = cr.getContractAst();

        System.out.println(toJson(ast));
    }
}
