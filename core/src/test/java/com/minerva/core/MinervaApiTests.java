package com.minerva.core;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.AutoConfigureRestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.client.RestClient;
import org.testcontainers.ollama.OllamaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;

import java.util.Objects;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
@Import(MinervaTestConfiguration.class)
@Tag("ContinuousIntegration")
public class MinervaApiTests {
    @Autowired
    private PostgreSQLContainer postgresContainer;

    @Autowired
    private OllamaContainer ollamaContainer;

    @Autowired
    private RestTestClient restTestClient;

    @LocalServerPort
    private int port;

    @Test
    void BasicInteraction() {
        restTestClient.post().uri(uriBuilder ->
                        uriBuilder.host("localhost").port(port)
                                .path("/message")
                                .queryParam("message", "Hello there")
                                .build())
                .exchange()
                .expectBody(String.class);
    }

    @Test
    void ImageInteraction() {
        restTestClient
                .post().uri(
                        uriBuilder ->
                                uriBuilder.host("localhost").port(port)
                                .path("/image").queryParam("message","What do you think about this image?")
                                        .queryParam("imagePath", "src/test/resources/TestImage.jpg")
                                        .build()).exchange().expectBody(String.class);
    }

    @Test
    void AlarmInteraction() {
        restTestClient
                .post().uri(
                        uriBuilder ->
                                uriBuilder.host("localhost").port(port).path("/message")
                                        .queryParam("message","Set an alarm for 7 AM tomorrow")
                                        .build())
                .exchange()
                .expectBody(String.class);
    }

    @Test
    @Disabled("Audio needs more thinking")
    void AudioInteraction() {
        restTestClient
                .post().uri(
                        uriBuilder ->
                                uriBuilder.host("localhost").port(port).path("/audio")
                                        .queryParam("message","Process this audio file")
                                        .queryParam("audioPath", "src/test/resources/test_audio.wav")
                                        .build())
                .exchange()
                .expectBody(String.class);
    }

}