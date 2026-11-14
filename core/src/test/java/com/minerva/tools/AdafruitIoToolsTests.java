package com.minerva.tools;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@Tag("Unit")
class AdafruitIoToolsTests {
    private static final String BASE_URL = "http://adafruit.test";
    private static final String USERNAME = "test-user";
    private static final String API_KEY = "test-api-key";

    private final String originalBaseUrl = AdafruitIoTools.baseUrl;
    private final String originalIoKey = AdafruitIoTools.ioKey;
    private final String originalIoUsername = AdafruitIoTools.ioUsername;

    @AfterEach
    void restoreConfiguration() {
        AdafruitIoTools.baseUrl = originalBaseUrl;
        AdafruitIoTools.ioKey = originalIoKey;
        AdafruitIoTools.ioUsername = originalIoUsername;
    }

    @Test
    void returnsFailureWhenCredentialsAreDisabled() throws IOException {
        var tool = new TestableAdafruitIoTools(RestClient.builder().build(), true);
        tool.enabled = false;

        assertEquals("Failed! IO key & username not found", tool.getLatestValueFromFeed("temperature"));
    }

    @Test
    void returnsFailureWhenAdafruitIoCannotBeReached() throws IOException {
        var tool = new TestableAdafruitIoTools(RestClient.builder().build(), false);

        assertEquals("Failed! Cannot connect to Adafruit.com", tool.getLatestValueFromFeed("temperature"));
    }

    @Test
    void returnsLatestFeedValueFromAdafruitIo() throws IOException {
        AdafruitIoTools.baseUrl = BASE_URL;
        AdafruitIoTools.ioKey = API_KEY;
        AdafruitIoTools.ioUsername = USERNAME;
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var tool = new TestableAdafruitIoTools(builder.build(), true);
        AdafruitIoTools.ioUsername = USERNAME;
        server.expect(requestTo(BASE_URL + "/api/v2/" + USERNAME + "/feeds/temperature/data?limit=1"))
                .andExpect(method(GET))
                .andExpect(header("X-AIO-Key", AdafruitIoTools.ioKey))
                .andRespond(withSuccess("{\"value\":\"23.5\"}", MediaType.APPLICATION_JSON));

        assertEquals("23.5", tool.getLatestValueFromFeed("temperature"));
        server.verify();
    }

    private static class TestableAdafruitIoTools extends AdafruitIoTools {
        private final boolean connectable;

        TestableAdafruitIoTools(RestClient ioClient, boolean connectable) {
            super(ioClient);
            this.connectable = connectable;
            enabled = true;
        }

        @Override
        boolean canConnect() {
            return connectable;
        }
    }
}
