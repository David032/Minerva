package com.minerva.api.library;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("Unit")
class MediaLibraryApiControllerTests {
    @Test
    void delegatesBoundedListingWithCursor() throws Exception {
        var service = mock(MediaLibraryService.class);
        var page = new MediaLibraryPage(List.of(), null);
        when(service.listPage(12, "cursor")).thenReturn(page);
        var controller = new MediaLibraryApiController(service);

        assertEquals(page, controller.page(12, "cursor"));
        verify(service).listPage(12, "cursor");
    }

    @Test
    void returnsCreatedForAcceptedUrlCandidates() throws Exception {
        var service = mock(MediaLibraryService.class);
        var item = new MediaLibraryItem(
                "00000000-0000-0000-0000-000000000001", "Interesting article",
                "https://example.com/article", "text/plain", "article.txt",
                java.time.Instant.parse("2026-01-01T00:00:00Z"), "Relevant.");
        var submission = new MediaLibraryService.Submission(true, "Relevant.", item);
        when(service.submitUrl("https://example.com/article")).thenReturn(submission);
        var controller = new MediaLibraryApiController(service);

        var response = controller.submit("https://example.com/article", null);

        assertEquals(201, response.getStatusCode().value());
        assertEquals("/api/library/" + item.id(), response.getHeaders().getLocation().toString());
        assertEquals(submission, response.getBody());
        verify(service).submitUrl("https://example.com/article");
    }

    @Test
    void requiresExactlyOneCandidateSource() {
        var controller = new MediaLibraryApiController(mock(MediaLibraryService.class));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class, () -> controller.submit(" ", null));

        assertEquals("Submit exactly one public URL or file.", error.getMessage());
    }

    @Test
    void returnsBadGatewayForRemoteDownloadFailures() {
        var controller = new MediaLibraryApiController(mock(MediaLibraryService.class));

        var response = controller.downloadFailed(
                new MediaLibraryService.RemoteFetchException(
                        "Could not download the media candidate: Server unavailable.",
                        new java.io.IOException("Server unavailable.")));

        assertEquals(502, response.getStatusCode().value());
        assertEquals("Could not download the media candidate: Server unavailable.",
                response.getBody().get("error"));
    }
}
