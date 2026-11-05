package com.minerva.core;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.test.autoconfigure.AutoConfigureRestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.client.RestClient;
import org.testcontainers.ollama.OllamaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.web.servlet.client.RestTestClient;
import java.time.Duration;
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

    @LocalServerPort
    private int port;

    private RestTestClient restTestClient;


    @BeforeEach
    void configureRestTestClient() {
        var requestFactory = new JdkClientHttpRequestFactory();
        requestFactory.setReadTimeout(Duration.ofSeconds(27000));

        restTestClient = RestTestClient.bindToServer(requestFactory)
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    void BasicInteraction() {
        restTestClient.post().uri("/message")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("message=Hello+there")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class);
    }

    @Test
    void ChatPageAndClientAssetsAreServed() {
        restTestClient.get().uri("/")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> {
                    Assertions.assertTrue(body.contains("What can I help you with?"));
                    Assertions.assertTrue(body.contains("/chat.css"));
                    Assertions.assertTrue(body.contains("/chat.js"));
                });

        restTestClient.get().uri("/chat.js")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> Assertions.assertTrue(body.contains("fetch(\"/message\"")));

        restTestClient.get().uri("/chat.css")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> Assertions.assertTrue(body.contains(".message.user")));
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