package com.minerva.api.library;

import com.minerva.api.personality.MediaDocumentExtractor;
import com.minerva.api.personality.MediaPreprocessor;
import com.minerva.api.personality.RemoteArticleFetcher;
import com.minerva.api.personality.RemoteDocumentFetcher;
import com.minerva.api.personality.RemoteMediaFetcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("Unit")
class MediaLibraryServiceTests {
    private MediaDocumentExtractor extractor;
    private RemoteArticleFetcher articleFetcher;
    private RemoteDocumentFetcher documentFetcher;
    private RemoteMediaFetcher mediaFetcher;
    private MediaPreprocessor mediaPreprocessor;
    private MediaLibraryCurator curator;
    private MediaLibraryStore store;
    private MediaLibraryService service;

    @BeforeEach
    void setUp() {
        extractor = mock(MediaDocumentExtractor.class);
        articleFetcher = mock(RemoteArticleFetcher.class);
        documentFetcher = mock(RemoteDocumentFetcher.class);
        mediaFetcher = mock(RemoteMediaFetcher.class);
        mediaPreprocessor = mock(MediaPreprocessor.class);
        curator = mock(MediaLibraryCurator.class);
        store = mock(MediaLibraryStore.class);
        service = new MediaLibraryService(
                extractor, articleFetcher, documentFetcher, mediaFetcher,
                mediaPreprocessor, curator, store);
    }

    @Test
    void rejectedUploadIsNotPersisted() throws IOException {
        byte[] html = "<html><body>Candidate content</body></html>".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(extractor.extract("candidate.html", html)).thenReturn(
                new MediaDocumentExtractor.Extraction("Candidate content", List.of(), List.of(), List.of()));
        when(mediaFetcher.fetch(List.of())).thenReturn(List.of());
        when(mediaPreprocessor.prepare(List.of())).thenReturn(List.of());
        when(curator.decide("candidate.html", null, "text/html", "Candidate content", List.of()))
                .thenReturn(new MediaLibraryCurator.Decision(false, "Not relevant.", "A description."));

        var result = service.submitFile(new MockMultipartFile("file", "candidate.html", "text/html", html));

        assertFalse(result.accepted());
        assertEquals("Not relevant.", result.reason());
        assertEquals(null, result.item());
        verify(store, never()).save(anyString(), any(), anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void acceptedImageIsStoredOnlyAfterCuration() throws IOException {
        byte[] jpeg = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1};
        var prepared = List.of(new RemoteMediaFetcher.MediaAsset(
                URI.create("https://upload.invalid/image"), "image/jpeg", jpeg));
        var item = new MediaLibraryItem(
                "00000000-0000-0000-0000-000000000001", "image.jpg", null,
                "image/jpeg", "image.jpg", java.time.Instant.parse("2026-01-01T00:00:00Z"), "Relevant.");
        when(mediaPreprocessor.prepare(anyList())).thenReturn(prepared);
        when(curator.decide(eq("image.jpg"), isNull(), eq("image/jpeg"), eq(""), eq(prepared)))
                .thenReturn(new MediaLibraryCurator.Decision(true, "Relevant.", "A visual description."));
        when(store.save(eq("image.jpg"), isNull(), eq("image/jpeg"), eq("image.jpg"),
                eq("A visual description."), eq("Relevant."), any(byte[].class))).thenReturn(item);

        var result = service.submitFile(new MockMultipartFile("file", "image.jpg", "image/jpeg", jpeg));

        assertTrue(result.accepted());
        assertEquals(item, result.item());
        verify(store).save(eq("image.jpg"), isNull(), eq("image/jpeg"), eq("image.jpg"),
                eq("A visual description."), eq("Relevant."), any(byte[].class));
    }

    @Test
    void rejectedPublicArticleIsNotPersisted() throws IOException {
        String url = "https://example.com/article";
        var article = new RemoteArticleFetcher.Article(
                URI.create(url), "Article title", "Article text about an unrelated topic.");
        when(articleFetcher.fetch(url)).thenReturn(article);
        when(curator.decide(eq("Article title"), eq(url), eq("text/plain"), anyString(), eq(List.of())))
                .thenReturn(new MediaLibraryCurator.Decision(
                        false, "Not relevant.", "An article about an unrelated topic."));

        var result = service.submitUrl(url);

        assertFalse(result.accepted());
        verify(store, never()).save(anyString(), any(), anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void reportsRemoteDownloadFailuresExplicitly() throws IOException {
        String url = "https://example.com/article";
        when(articleFetcher.fetch(url)).thenThrow(new IOException("The article server did not respond."));

        var error = assertThrows(MediaLibraryService.RemoteFetchException.class, () -> service.submitUrl(url));

        assertTrue(error.getMessage().contains("The article server did not respond."));
        verify(store, never()).save(anyString(), any(), anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void rejectsOversizedUploadBeforeReadingItsBytes() throws IOException {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("large.png");
        when(file.getSize()).thenReturn(5L * 1024 * 1024 + 1);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.submitFile(file));

        assertEquals("Uploaded images must be 5 MB or smaller.", error.getMessage());
        verify(file, never()).getBytes();
        verify(curator, never()).decide(anyString(), any(), anyString(), anyString(), anyList());
    }
}
