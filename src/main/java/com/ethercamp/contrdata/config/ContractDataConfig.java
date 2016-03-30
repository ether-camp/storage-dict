package com.ethercamp.contrdata.config;

import org.ethereum.config.SystemProperties;
import org.ethereum.datasource.KeyValueDataSource;
import org.ethereum.datasource.LevelDbDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan("com.ethercamp.contrdata")
public class ContractDataConfig {

    @Bean
    public SystemProperties systemProperties() {
        return SystemProperties.CONFIG;
    }

    @Bean
    public KeyValueDataSource storageDict() {
        LevelDbDataSource dataSource = new LevelDbDataSource("storageDict");
        dataSource.init();
        return dataSource;
    }
}
