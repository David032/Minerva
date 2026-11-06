package com.minerva.utils;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

@Tag("Unit")
class OllamaConnectorTests {
    private static final String BASE_URL = "http://ollama.test";
    private static final String MODEL = "gemma4:e2b";

    @Test
    void reportsWhenModelIsAlreadyAvailable() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE_URL + "/api/tags"))
                .andExpect(method(GET))
                .andRespond(withSuccess("{\"models\":[{\"name\":\"" + MODEL + "\"}]}", MediaType.APPLICATION_JSON));
        var connector = new OllamaConnector(BASE_URL, builder);

        assertTrue(connector.checkForModel(MODEL));
        server.verify();
    }

    @Test
    void reportsWhenModelIsAbsent() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE_URL + "/api/tags"))
                .andExpect(method(GET))
                .andRespond(withSuccess("{\"models\":[]}", MediaType.APPLICATION_JSON));
        var connector = new OllamaConnector(BASE_URL, builder);

        assertFalse(connector.checkForModel(MODEL));
        server.verify();
    }

    @Test
    void propagatesOllamaHttpErrors() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE_URL + "/api/tags"))
                .andExpect(method(GET))
                .andRespond(withServerError());
        var connector = new OllamaConnector(BASE_URL, builder);

        assertThrows(RestClientResponseException.class, () -> connector.checkForModel(MODEL));
        server.verify();
    }

    @Test
    void pullsAbsentModelAndWaitsUntilItAppears() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(BASE_URL + "/api/tags"))
                .andExpect(method(GET))
                .andRespond(withSuccess("{\"models\":[]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE_URL + "/api/pull"))
                .andExpect(method(POST))
                .andExpect(content().json("{\"model\":\"" + MODEL + "\"}"))
                .andRespond(withSuccess("{\"status\":\"success\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE_URL + "/api/tags"))
                .andExpect(method(GET))
                .andRespond(withSuccess("{\"models\":[{\"name\":\"" + MODEL + "\"}]}", MediaType.APPLICATION_JSON));
        var connector = new OllamaConnector(BASE_URL, builder);

        connector.connectToOllama(BASE_URL, MODEL);

        server.verify();
    }
}
