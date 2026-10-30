package com.minerva.core;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.AutoConfigureRestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.testcontainers.ollama.OllamaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestClient
@Tag("ContinuousIntegration")
public class MinervaApiTests {

    @Test
    void BasicInteraction(@Autowired RestTestClient restClient) {
        var response = restClient
                .post().uri(
                        uriBuilder ->
                                uriBuilder.queryParam("message","Hello there")
                                .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class);
    }

    @Test
    void ImageInteraction(@Autowired RestTestClient restClient) {
        var response = restClient
                .post().uri(
                        uriBuilder ->
                                uriBuilder.queryParam("message","What do you think about this image?")
                                        .queryParam("imagePath", "src/test/resources/TestImage.jpg")
                                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class);
    }

    @Test
    void AlarmInteraction(@Autowired RestTestClient restClient) {
        var response = restClient
                .post().uri(
                        uriBuilder ->
                                uriBuilder.queryParam("message","Set an alarm for 7 AM tomorrow")
                                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class);
    }

    @Test
    @Disabled("Audio needs more thinking")
    void AudioInteraction(@Autowired RestTestClient restClient) {
        var response = restClient
                .post().uri(
                        uriBuilder ->
                                uriBuilder.queryParam("message","Process this audio file")
                                        .queryParam("audioPath", "src/test/resources/test_audio.wav")
                                        .build())
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class);
    }

}
