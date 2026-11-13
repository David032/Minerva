package com.minerva.api.personality;

import com.minerva.core.personality.PersonalityPromptStore;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("Unit")
class MediaPersonalityServiceTests {
    @TempDir
    Path tempDirectory;

    @Test
    void processesUploadPersistsMemoriesAndUpdatesPersonality() throws Exception {
        var promptStore = new PersonalityPromptStore(tempDirectory.resolve("personality.md"));
        var memoryStore = new InterestMemoryStore(tempDirectory.resolve("memories.md"));
        var fetcher = mock(RemoteMediaFetcher.class);
        var analyzer = mock(PersonalityAnalyzer.class);
        when(fetcher.fetch(List.of())).thenReturn(List.of());
        when(analyzer.analyze(any(), eq(List.of()), eq(""), eq("I like this"))).thenReturn("I enjoy botanical art.");
        var service = new MediaPersonalityService(new MediaDocumentExtractor(), fetcher,
                new MediaPreprocessor(video -> List.of()), analyzer, promptStore, memoryStore);
        var file = new MockMultipartFile("file", "likes.html", "text/html",
                "<p>Botanical illustrations</p><span class=\"tag\">Botanical art</span>".getBytes());

        var result = service.process(file, "I like this");

        assertEquals(1, result.savedTagCount());
        assertEquals(0, result.analyzedMediaCount());
        assertEquals("I enjoy botanical art.", promptStore.load());
        assertTrue(Files.readString(tempDirectory.resolve("memories.md")).contains("- Botanical art"));
    }

    @Test
    void doesNotPersistPromptOrTagsWhenAnalysisFails() throws Exception {
        var promptStore = new PersonalityPromptStore(tempDirectory.resolve("personality.md"));
        var memoryStore = new InterestMemoryStore(tempDirectory.resolve("memories.md"));
        promptStore.save("Existing preferences");
        var fetcher = mock(RemoteMediaFetcher.class);
        var analyzer = mock(PersonalityAnalyzer.class);
        when(fetcher.fetch(List.of())).thenReturn(List.of());
        when(analyzer.analyze(any(), any(), any(), any())).thenThrow(new IllegalStateException("model unavailable"));
        var service = new MediaPersonalityService(new MediaDocumentExtractor(), fetcher,
                new MediaPreprocessor(video -> List.of()), analyzer, promptStore, memoryStore);
        var file = new MockMultipartFile("file", "likes.html", "text/html",
                "<span class=\"tag\">Botanical art</span>".getBytes());

        assertThrows(IOException.class, () -> service.process(file, ""));

        assertEquals("Existing preferences", promptStore.load());
        assertTrue(Files.notExists(tempDirectory.resolve("memories.md")));
    }

    @Test
    void rollsBackTagMemoriesWhenPromptPersistenceFails() throws Exception {
        Path promptPath = tempDirectory.resolve("personality.md");
        new PersonalityPromptStore(promptPath).save("Existing preferences");
        var promptStore = new FailingPersonalityPromptStore(promptPath);
        var memoryPath = tempDirectory.resolve("memories.md");
        var memoryStore = new InterestMemoryStore(memoryPath);
        var fetcher = mock(RemoteMediaFetcher.class);
        var analyzer = mock(PersonalityAnalyzer.class);
        when(fetcher.fetch(List.of())).thenReturn(List.of());
        when(analyzer.analyze(any(), any(), any(), any())).thenReturn("Updated preferences");
        var service = new MediaPersonalityService(new MediaDocumentExtractor(), fetcher,
                new MediaPreprocessor(video -> List.of()), analyzer, promptStore, memoryStore);
        var file = new MockMultipartFile("file", "likes.html", "text/html",
                "<span class=\"tag\">Botanical art</span>".getBytes());

        assertThrows(IOException.class, () -> service.process(file, ""));

        assertEquals("Existing preferences", new PersonalityPromptStore(promptPath).load());
        assertTrue(Files.notExists(memoryPath));
    }

    @Test
    void leavesPromptUntouchedWhenReferencedMediaCannotBeFetched() throws Exception {
        Path promptPath = tempDirectory.resolve("personality.md");
        var promptStore = new PersonalityPromptStore(promptPath);
        promptStore.save("Existing preferences");
        var memoryPath = tempDirectory.resolve("memories.md");
        var fetcher = mock(RemoteMediaFetcher.class);
        var analyzer = mock(PersonalityAnalyzer.class);
        when(fetcher.fetch(any())).thenThrow(new IOException("Could not retrieve referenced media."));
        var service = new MediaPersonalityService(new MediaDocumentExtractor(), fetcher,
                new MediaPreprocessor(video -> List.of()), analyzer, promptStore, new InterestMemoryStore(memoryPath));
        var file = new MockMultipartFile("file", "likes.html", "text/html",
                "<img src=\"https://media.example.test/image.png\"><span class=\"tag\">Art</span>".getBytes());

        assertThrows(IOException.class, () -> service.process(file, ""));

        assertEquals("Existing preferences", promptStore.load());
        assertTrue(Files.notExists(memoryPath));
    }

    @Test
    void preprocessesFetchedVideoFramesBeforeLocalModelAnalysis() throws Exception {
        var promptStore = new PersonalityPromptStore(tempDirectory.resolve("personality.md"));
        var fetcher = mock(RemoteMediaFetcher.class);
        var analyzer = mock(PersonalityAnalyzer.class);
        var video = new RemoteMediaFetcher.MediaAsset(
                java.net.URI.create("https://media.example.test/clip.mp4"), "video/mp4", new byte[] {1, 2});
        byte[] frame = new byte[] {3, 4, 5};
        when(fetcher.fetch(any())).thenReturn(List.of(video));
        when(analyzer.analyze(any(), any(), any(), any())).thenAnswer(invocation -> {
            List<RemoteMediaFetcher.MediaAsset> prepared = invocation.getArgument(1);
            assertEquals(1, prepared.size());
            assertEquals("image/png", prepared.getFirst().contentType());
            assertArrayEquals(frame, prepared.getFirst().content());
            return "I enjoy this video.";
        });
        var service = new MediaPersonalityService(new MediaDocumentExtractor(), fetcher,
                new MediaPreprocessor(ignored -> List.of(frame)), analyzer, promptStore,
                new InterestMemoryStore(tempDirectory.resolve("memories.md")));
        var file = new MockMultipartFile("file", "clip.html", "text/html",
                "<video src=\"https://media.example.test/clip.mp4\"></video>".getBytes());

        var result = service.process(file, "");

        assertEquals(1, result.analyzedMediaCount());
        assertEquals("I enjoy this video.", promptStore.load());
    }

    private static class FailingPersonalityPromptStore extends PersonalityPromptStore {
        FailingPersonalityPromptStore(Path path) {
            super(path);
        }

        @Override
        public synchronized void save(String prompt) throws IOException {
            throw new IOException("Prompt storage unavailable.");
        }
    }
}
