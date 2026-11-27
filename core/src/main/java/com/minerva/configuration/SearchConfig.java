package com.minerva.configuration;

import org.springaicommunity.agent.tools.BraveWebSearchTool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SearchConfig {

    /**
     * Creates the Brave search tool only when its optional API key is configured.
     *
     * @param apiKey the configured Brave API key
     * @return the Brave web search tool
     */
    @Bean
    @Conditional(BraveApiKeyConfiguredCondition.class)
    public BraveWebSearchTool braveWebSearchTool(@Value("${brave.api.key:}") String apiKey) {
        return BraveWebSearchTool.builder(apiKey)
                .resultCount(10)
                .build();
    }
}
