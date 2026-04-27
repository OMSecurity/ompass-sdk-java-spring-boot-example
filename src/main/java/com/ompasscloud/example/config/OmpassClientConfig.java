package com.ompasscloud.example.config;

import com.ompasscloud.sdk.OmpassClient;
import com.ompasscloud.sdk.OmpassConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OmpassClientConfig {

    @Bean
    public OmpassClient ompassClient(OmpassProperties properties) {
        OmpassConfig config = OmpassConfig.builder()
                .clientId(properties.getClientId())
                .secretKey(properties.getSecretKey())
                .baseUrl(properties.getBaseUrl())
                .build();

        return new OmpassClient(config);
    }
}
