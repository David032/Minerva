package com.minerva.core;

import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.ollama.OllamaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;
import java.io.IOException;

@TestConfiguration(proxyBeanMethods = false)
public class MinervaTestConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        var postgresqlContainer = new PostgreSQLContainer("postgres:17-alpine")
                .withDatabaseName("postgres")
                .withUsername("postgres")
                .withPassword("ThingExplainer");

        return postgresqlContainer;
    }

    @Bean
    @ServiceConnection
    OllamaContainer ollamaContainer() throws IOException, InterruptedException {
        var ollamaContainer = new OllamaContainer("ollama/ollama:latest").withStartupTimeout(java.time.Duration.ofMinutes(25))
                .waitingFor(org.testcontainers.containers.wait.strategy.Wait.forHttp("/api/tags").forStatusCode(200));
        ollamaContainer.start();
        ollamaContainer.execInContainer("ollama", "pull", "gemma4:e2b");
        return ollamaContainer;
    }

    @Bean
    @Primary
    DataSource dataSource(PostgreSQLContainer postgresContainer) {
        return DataSourceBuilder.create()
                .driverClassName("org.postgresql.Driver")
                .url(postgresContainer.getJdbcUrl())
                .username(postgresContainer.getUsername())
                .password(postgresContainer.getPassword())
                .build();
    }
}
