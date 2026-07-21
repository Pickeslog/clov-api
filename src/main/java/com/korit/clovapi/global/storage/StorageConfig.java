package com.korit.clovapi.global.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {

    @Bean(destroyMethod = "close")
    public StoragePresigner storagePresigner(StorageProperties properties) {
        return new StoragePresigner(properties);
    }
}
