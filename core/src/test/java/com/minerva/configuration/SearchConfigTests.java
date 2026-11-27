package com.minerva.configuration;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springaicommunity.agent.tools.BraveWebSearchTool;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("Unit")
class SearchConfigTests {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SearchConfig.class);

    @Test
    void doesNotRegisterSearchToolWhenApiKeyIsMissing() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(BraveWebSearchTool.class));
    }

    @Test
    void doesNotRegisterSearchToolWhenApiKeyIsBlank() {
        contextRunner.withPropertyValues("brave.api.key=")
                .run(context -> assertThat(context).doesNotHaveBean(BraveWebSearchTool.class));
    }

    @Test
    void doesNotRegisterSearchToolWhenApiKeyContainsOnlyWhitespace() {
        contextRunner.withPropertyValues("brave.api.key=   ")
                .run(context -> assertThat(context).doesNotHaveBean(BraveWebSearchTool.class));
    }

    @Test
    void registersSearchToolWhenApiKeyIsConfigured() {
        contextRunner.withPropertyValues("brave.api.key=test-api-key")
                .run(context -> assertThat(context).hasSingleBean(BraveWebSearchTool.class));
    }
}
