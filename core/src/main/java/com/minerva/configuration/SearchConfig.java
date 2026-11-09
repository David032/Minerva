package com.minerva.configuration;

import org.springaicommunity.agent.tools.BraveWebSearchTool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SearchConfig {

    @Bean
    public BraveWebSearchTool braveWebSearchTool(@Value("${brave.api.key}") String apiKey) {
        return BraveWebSearchTool.builder(apiKey)
                .resultCount(10)
                .build();
    }
}
