package com.minerva.core;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.ollama.OllamaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.IOException;

@TestConfiguration(proxyBeanMethods = false)
public class MinervaTestConfiguration {
    @Bean
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer("postgres:17-alpine")
                .withDatabaseName("postgres")
                .withUsername("postgres")
                .withPassword("ThingExplainer");
    }
    @Bean
    OllamaContainer ollamaContainer() throws IOException, InterruptedException {
        var ollamaContainer = new OllamaContainer("ollama/ollama:latest").withStartupTimeout(java.time.Duration.ofMinutes(25))
                .waitingFor(org.testcontainers.containers.wait.strategy.Wait.forHttp("/api/tags").forStatusCode(200));
        ollamaContainer.start();
        ollamaContainer.execInContainer("ollama", "pull", "gemma4:e2b");
        return ollamaContainer;
    }
}
