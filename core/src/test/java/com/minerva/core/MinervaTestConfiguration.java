package com.minerva.core;

import org.springframework.ai.model.ollama.autoconfigure.OllamaConnectionDetails;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.testcontainers.ollama.OllamaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.springframework.web.client.RestClient;

import javax.sql.DataSource;
import java.io.IOException;
import java.time.Duration;

@TestConfiguration(proxyBeanMethods = false)
public class MinervaTestConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer("postgres:17-alpine")
                .withDatabaseName("postgres")
                .withUsername("postgres")
                .withPassword("ThingExplainer");
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

    /**
     * Configures a longer Ollama response timeout for slow model inference in integration tests.
     *
     * @param connectionDetails the active Ollama endpoint, including its Testcontainers-mapped port
     * @param restClientBuilders Spring Boot's configured REST client builders, when available
     * @return the Ollama API client configured with a ten-minute read timeout
     */
    @Bean
    OllamaApi ollamaApi(
            OllamaConnectionDetails connectionDetails,
            ObjectProvider<RestClient.Builder> restClientBuilders
    ) {
        var requestFactory = new JdkClientHttpRequestFactory();
        requestFactory.setReadTimeout(Duration.ofMinutes(10));

        var restClientBuilder = restClientBuilders.getIfAvailable(RestClient::builder)
                .requestFactory(requestFactory);
        return OllamaApi.builder()
                .baseUrl(connectionDetails.getBaseUrl())
                .restClientBuilder(restClientBuilder)
                .build();
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
