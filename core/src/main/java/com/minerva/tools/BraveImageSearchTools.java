package com.minerva.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Exposes Brave image search as a tool independent from Brave web search.
 */
public class BraveImageSearchTools {

    private static final String API_BASE_URL = "https://api.search.brave.com";
    private static final int RESULT_COUNT = 10;

    private final RestClient restClient;

    /**
     * Creates image-search tools authenticated with a Brave Search API key.
     *
     * @param apiKey Brave Search API subscription key
     */
    public BraveImageSearchTools(String apiKey) {
        this(apiKey, RestClient.builder());
    }

    BraveImageSearchTools(String apiKey, RestClient.Builder restClientBuilder) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("Brave Search API key must not be blank");
        }
        this.restClient = restClientBuilder
                .baseUrl(API_BASE_URL)
                .defaultHeader("X-Subscription-Token", apiKey)
                .build();
    }

    /**
     * Searches Brave for images and returns their URLs and identifying metadata.
     *
     * @param query terms describing the images to find
     * @return image result metadata from Brave
     * @throws IllegalArgumentException if the query is blank
     * @throws IllegalStateException if Brave returns an invalid response
     */
    @Tool(description = "Search Brave for images matching a query and return image URLs and source metadata")
    public List<ImageSearchResult> searchImages(String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Image search query must not be blank");
        }

        JsonNode response = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/res/v1/images/search")
                        .queryParam("q", query)
                        .queryParam("count", RESULT_COUNT)
                        .build())
                .retrieve()
                .body(JsonNode.class);

        JsonNode results = response == null ? null : response.path("results");
        if (results == null || !results.isArray()) {
            throw new IllegalStateException("Brave image search returned an invalid response");
        }

        List<ImageSearchResult> imageResults = new ArrayList<>();
        for (JsonNode result : results) {
            JsonNode properties = result.path("properties");
            JsonNode thumbnail = result.path("thumbnail");
            imageResults.add(new ImageSearchResult(
                    text(result, "title"),
                    text(properties, "url"),
                    text(result, "url"),
                    text(thumbnail, "src"),
                    integer(properties, "width"),
                    integer(properties, "height")));
        }
        return List.copyOf(imageResults);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private static Integer integer(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.canConvertToInt() ? value.intValue() : null;
    }

    /**
     * Image details returned by Brave's image search.
     *
     * @param title image result title
     * @param imageUrl direct URL of the image
     * @param sourceUrl URL of the page containing the image
     * @param thumbnailUrl URL of the image thumbnail
     * @param width image width in pixels, when available
     * @param height image height in pixels, when available
     */
    public record ImageSearchResult(
            String title,
            String imageUrl,
            String sourceUrl,
            String thumbnailUrl,
            Integer width,
            Integer height) {
    }
}
