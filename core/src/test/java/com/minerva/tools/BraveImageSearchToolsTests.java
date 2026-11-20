package com.minerva.tools;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@Tag("Unit")
class BraveImageSearchToolsTests {
    private static final String API_KEY = "test-api-key";
    private static final String SEARCH_URL =
            "https://api.search.brave.com/res/v1/images/search?q=red%20fox&count=10";

    @Test
    void searchesForImagesAndMapsResultMetadata() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var tools = new BraveImageSearchTools(API_KEY, builder);
        server.expect(requestTo(SEARCH_URL))
                .andExpect(method(GET))
                .andExpect(header("X-Subscription-Token", API_KEY))
                .andRespond(withSuccess("""
                        {
                          "results": [{
                            "title": "Red fox",
                            "url": "https://example.com/fox",
                            "thumbnail": {"src": "https://thumb.example/fox"},
                            "properties": {
                              "url": "https://images.example/fox.jpg",
                              "width": 640,
                              "height": 480
                            }
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));

        var results = tools.searchImages("red fox");

        assertEquals(1, results.size());
        assertEquals(new BraveImageSearchTools.ImageSearchResult(
                "Red fox",
                "https://images.example/fox.jpg",
                "https://example.com/fox",
                "https://thumb.example/fox",
                640,
                480), results.getFirst());
        server.verify();
    }

    @Test
    void propagatesBraveApiErrors() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var tools = new BraveImageSearchTools(API_KEY, builder);
        server.expect(requestTo(SEARCH_URL))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThrows(RestClientResponseException.class, () -> tools.searchImages("red fox"));
        server.verify();
    }

    @Test
    void rejectsBlankQueriesWithoutCallingBrave() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var tools = new BraveImageSearchTools(API_KEY, builder);

        assertThrows(IllegalArgumentException.class, () -> tools.searchImages(" "));
        server.verify();
    }

    @Test
    void rejectsResponsesWithoutAnImageResultsArray() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var tools = new BraveImageSearchTools(API_KEY, builder);
        server.expect(requestTo(SEARCH_URL))
                .andRespond(withSuccess("{\"type\":\"images\"}", MediaType.APPLICATION_JSON));

        assertThrows(IllegalStateException.class, () -> tools.searchImages("red fox"));
        server.verify();
    }
}
