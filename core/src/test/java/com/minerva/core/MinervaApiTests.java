package com.minerva.core;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.AutoConfigureRestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.client.RestClient;
import org.testcontainers.ollama.OllamaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.Objects;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestClient
@Import(MinervaTestConfiguration.class)
@Tag("ContinuousIntegration")
public class MinervaApiTests {
    @Autowired
    private static PostgreSQLContainer postgresContainer;

    @Autowired
    private static OllamaContainer ollamaContainer;

    @Test
    void BasicInteraction(@Autowired RestClient restClient) {
        var response = Objects.requireNonNull(restClient
                        .post().uri(
                                uriBuilder ->
                                        uriBuilder.queryParam("message", "Hello there")
                                                .build())).retrieve().toEntity(String.class);
        assert response.getBody() != null;
    }

    @Test
    void ImageInteraction(@Autowired RestClient restClient) {
        var response = restClient
                .post().uri(
                        uriBuilder ->
                                uriBuilder.queryParam("message","What do you think about this image?")
                                        .queryParam("imagePath", "src/test/resources/TestImage.jpg")
                                        .build()).retrieve().toEntity(String.class);
        assert response.getBody() != null;
    }

    @Test
    void AlarmInteraction(@Autowired RestClient restClient) {
        var response = restClient
                .post().uri(
                        uriBuilder ->
                                uriBuilder.queryParam("message","Set an alarm for 7 AM tomorrow")
                                        .build())
                .retrieve()
                .toEntity(String.class);
        assert response.getBody() != null;
    }

    @Test
    @Disabled("Audio needs more thinking")
    void AudioInteraction(@Autowired RestClient restClient) {
        var response = restClient
                .post().uri(
                        uriBuilder ->
                                uriBuilder.queryParam("message","Process this audio file")
                                        .queryParam("audioPath", "src/test/resources/test_audio.wav")
                                        .build())
                .retrieve()
                .toEntity(String.class);
        assert response.getBody() != null;
    }

}
