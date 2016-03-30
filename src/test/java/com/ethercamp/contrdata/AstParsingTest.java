package com.ethercamp.contrdata;

import com.ethercamp.contrdata.contract.Ast;
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
}
