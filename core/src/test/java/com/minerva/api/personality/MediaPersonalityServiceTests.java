package com.minerva.api.personality;

import com.minerva.core.personality.PersonalityPromptStore;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
        var imageOpinionService = mock(PersonalityImageOpinionService.class);
        when(fetcher.fetch(List.of())).thenReturn(List.of());
        when(analyzer.analyze(any(), eq(List.of()), eq(""), eq("I like this"))).thenAnswer(invocation -> {
            MediaDocumentExtractor.Extraction extraction = invocation.getArgument(0);
            assertEquals(List.of("A garden in spring"), extraction.imageAltTexts());
            return "I enjoy botanical art.";
        });
        var service = new MediaPersonalityService(new MediaDocumentExtractor(), fetcher,
                new MediaPreprocessor(video -> List.of()), analyzer, imageOpinionService, promptStore, memoryStore);
        var file = new MockMultipartFile("file", "likes.html", "text/html",
                "<p>Botanical illustrations</p><img alt=\"A garden in spring\">"
                        .concat("<span class=\"tag\">Botanical art</span>").getBytes());

        var result = service.process(file, "I like this");

        assertEquals(1, result.savedTagCount());
        assertEquals(0, result.analyzedMediaCount());
        assertEquals("I enjoy botanical art.", promptStore.load());
        assertTrue(Files.readString(tempDirectory.resolve("memories.md")).contains("- Botanical art"));
    }

    @Test
    void processesHtmlBatchesIndependentlyAndReportsFailuresByFilename() throws Exception {
        var promptStore = new PersonalityPromptStore(tempDirectory.resolve("personality.md"));
        var memoryStore = new InterestMemoryStore(tempDirectory.resolve("memories.md"));
        var fetcher = mock(RemoteMediaFetcher.class);
        var analyzer = mock(PersonalityAnalyzer.class);
        when(fetcher.fetch(List.of())).thenReturn(List.of());
        when(analyzer.analyze(any(), any(), any(), any())).thenAnswer(invocation -> {
            MediaDocumentExtractor.Extraction extraction = invocation.getArgument(0);
            if (extraction.text().contains("Broken")) {
                throw new IllegalStateException("Model unavailable.");
            }
            return "Updated from a valid page.";
        });
        var service = new MediaPersonalityService(new MediaDocumentExtractor(), fetcher,
                new MediaPreprocessor(video -> List.of()), analyzer, mock(PersonalityImageOpinionService.class),
                promptStore, memoryStore);
        List<org.springframework.web.multipart.MultipartFile> files = List.of(
                new MockMultipartFile("htmlFiles", "broken.html", "text/html", "<p>Broken</p>".getBytes()),
                new MockMultipartFile("htmlFiles", "good.html", "text/html",
                        "<span class=\"tag\">Botanical art</span>".getBytes()),
                new MockMultipartFile("htmlFiles", "not-html.pdf", "application/pdf", "%PDF-1.7".getBytes())
        );

        var result = service.processHtmlBatch(files, "");

        assertEquals(1, result.successfulFileCount());
        assertEquals(1, result.savedTagCount());
        assertEquals(2, result.failures().size());
        assertTrue(result.failures().stream().anyMatch(failure -> failure.filename().equals("broken.html")
                && failure.message().contains("Media analysis failed")));
        assertTrue(result.failures().stream().anyMatch(failure -> failure.filename().equals("not-html.pdf")
                && failure.message().contains("Only HTML files")));
        assertEquals("Updated from a valid page.", result.personalityPrompt());
        assertTrue(Files.readString(tempDirectory.resolve("memories.md")).contains("- Botanical art"));
    }

    @Test
    void processesImageBatchesIndependentlyIncludingImagesLargerThanTenMegabytes() throws Exception {
        var promptStore = new PersonalityPromptStore(tempDirectory.resolve("personality.md"));
        var imageOpinionService = mock(PersonalityImageOpinionService.class);
        var analyzer = mock(PersonalityAnalyzer.class);
        when(imageOpinionService.analyze(any(), any())).thenReturn("An image description.");
        when(analyzer.analyze(any(), any(), any(), any())).thenReturn("Updated from image.");
        var service = new MediaPersonalityService(new MediaDocumentExtractor(), mock(RemoteMediaFetcher.class),
                new MediaPreprocessor(video -> List.of()), analyzer, imageOpinionService, promptStore,
                new InterestMemoryStore(tempDirectory.resolve("memories.md")));
        byte[] png = new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 1};
        byte[] largePng = new byte[10 * 1024 * 1024 + 1];
        System.arraycopy(png, 0, largePng, 0, png.length);
        List<org.springframework.web.multipart.MultipartFile> files = List.of(
                new MockMultipartFile("imageFiles", "garden.png", "image/png", png),
                new MockMultipartFile("imageFiles", "broken.png", "image/png", new byte[] {1}),
                new MockMultipartFile("imageFiles", "large.png", "image/png", largePng)
        );

        var result = service.processImageBatch(files, "I like these.");

        assertEquals(2, result.successfulFileCount());
        assertEquals(2, result.analyzedMediaCount());
        assertEquals(List.of(new MediaPersonalityService.FileFailure(
                "broken.png", "The uploaded file is not a supported JPEG, PNG, GIF, or WebP image.")),
                result.failures());
        assertEquals("Updated from image.", result.personalityPrompt());
        org.mockito.Mockito.verify(imageOpinionService, org.mockito.Mockito.times(2)).analyze(any(), any());
    }

    @Test
    void doesNotPersistPromptOrTagsWhenAnalysisFails() throws Exception {
        var promptStore = new PersonalityPromptStore(tempDirectory.resolve("personality.md"));
        var memoryStore = new InterestMemoryStore(tempDirectory.resolve("memories.md"));
        promptStore.save("Existing preferences");
        var fetcher = mock(RemoteMediaFetcher.class);
        var analyzer = mock(PersonalityAnalyzer.class);
        var imageOpinionService = mock(PersonalityImageOpinionService.class);
        when(fetcher.fetch(List.of())).thenReturn(List.of());
        when(analyzer.analyze(any(), any(), any(), any())).thenThrow(new IllegalStateException("model unavailable"));
        var service = new MediaPersonalityService(new MediaDocumentExtractor(), fetcher,
                new MediaPreprocessor(video -> List.of()), analyzer, imageOpinionService, promptStore, memoryStore);
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
        var imageOpinionService = mock(PersonalityImageOpinionService.class);
        when(fetcher.fetch(List.of())).thenReturn(List.of());
        when(analyzer.analyze(any(), any(), any(), any())).thenReturn("Updated preferences");
        var service = new MediaPersonalityService(new MediaDocumentExtractor(), fetcher,
                new MediaPreprocessor(video -> List.of()), analyzer, imageOpinionService, promptStore, memoryStore);
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
        var imageOpinionService = mock(PersonalityImageOpinionService.class);
        when(fetcher.fetch(any())).thenThrow(new IOException("Could not retrieve referenced media."));
        var service = new MediaPersonalityService(new MediaDocumentExtractor(), fetcher,
                new MediaPreprocessor(video -> List.of()), analyzer, imageOpinionService, promptStore,
                new InterestMemoryStore(memoryPath));
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
        var imageOpinionService = mock(PersonalityImageOpinionService.class);
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
                new MediaPreprocessor(ignored -> List.of(frame)), analyzer, imageOpinionService, promptStore,
                new InterestMemoryStore(tempDirectory.resolve("memories.md")));
        var file = new MockMultipartFile("file", "clip.html", "text/html",
                "<video src=\"https://media.example.test/clip.mp4\"></video>".getBytes());

        var result = service.process(file, "");

        assertEquals(1, result.analyzedMediaCount());
        assertEquals("I enjoy this video.", promptStore.load());
    }

    @Test
    void asksForAnImageDescriptionWhenTheImageHasNoComment() throws Exception {
        var promptStore = new PersonalityPromptStore(tempDirectory.resolve("personality.md"));
        var fetcher = mock(RemoteMediaFetcher.class);
        var analyzer = mock(PersonalityAnalyzer.class);
        var imageOpinionService = mock(PersonalityImageOpinionService.class);
        byte[] imageBytes = new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 1};
        var image = new MockMultipartFile("file", "photo.png", "image/png", imageBytes);
        when(imageOpinionService.analyze(any(), eq("What is this image about?")))
                .thenReturn("A watercolor painting of a quiet garden.");
        when(analyzer.analyze(any(), eq(List.of()), eq(""), eq("")))
                .thenAnswer(invocation -> {
                    MediaDocumentExtractor.Extraction extraction = invocation.getArgument(0);
                    assertTrue(extraction.text().contains("A watercolor painting of a quiet garden."));
                    return "I enjoy calm watercolor garden scenes.";
                });
        var service = new MediaPersonalityService(new MediaDocumentExtractor(), fetcher,
                new MediaPreprocessor(video -> List.of()), analyzer, imageOpinionService, promptStore,
                new InterestMemoryStore(tempDirectory.resolve("memories.md")));

        var result = service.processImage(image, null, "");

        assertEquals(1, result.analyzedMediaCount());
        assertEquals("I enjoy calm watercolor garden scenes.", promptStore.load());
    }

    @Test
    void usesAnImageCommentAsTheImageAnalysisRequest() throws Exception {
        var promptStore = new PersonalityPromptStore(tempDirectory.resolve("personality.md"));
        var fetcher = mock(RemoteMediaFetcher.class);
        var analyzer = mock(PersonalityAnalyzer.class);
        var imageOpinionService = mock(PersonalityImageOpinionService.class);
        byte[] imageBytes = new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 1};
        var image = new MockMultipartFile("file", "photo.jpg", "image/jpeg", imageBytes);
        when(imageOpinionService.analyze(any(), eq("I love the warm colors.")))
                .thenReturn("The image uses warm colors.");
        when(analyzer.analyze(any(), eq(List.of()), eq(""), eq("I love the warm colors.")))
                .thenAnswer(invocation -> {
                    MediaDocumentExtractor.Extraction extraction = invocation.getArgument(0);
                    assertTrue(extraction.text().contains("The image uses warm colors."));
                    return "I enjoy warm, inviting color palettes.";
                });
        var service = new MediaPersonalityService(new MediaDocumentExtractor(), fetcher,
                new MediaPreprocessor(video -> List.of()), analyzer, imageOpinionService, promptStore,
                new InterestMemoryStore(tempDirectory.resolve("memories.md")));

        var result = service.processImage(image, null, "I love the warm colors.");

        assertEquals(1, result.analyzedMediaCount());
        assertEquals("I enjoy warm, inviting color palettes.", promptStore.load());
        org.mockito.Mockito.verify(imageOpinionService).analyze(any(), eq("I love the warm colors."));
    }

    @Test
    void processesAnImageUrlWithItsComment() throws Exception {
        var promptStore = new PersonalityPromptStore(tempDirectory.resolve("personality.md"));
        var fetcher = mock(RemoteMediaFetcher.class);
        var analyzer = mock(PersonalityAnalyzer.class);
        var imageOpinionService = mock(PersonalityImageOpinionService.class);
        var uri = java.net.URI.create("https://images.example.test/photo.png");
        var image = new RemoteMediaFetcher.MediaAsset(uri, "image/png", new byte[] {1, 2, 3});
        when(fetcher.fetch(List.of(uri))).thenReturn(List.of(image));
        when(imageOpinionService.analyze(any(), eq("I like this scene.")))
                .thenReturn("A scenic illustration.");
        when(analyzer.analyze(any(), eq(List.of()), eq(""), eq("I like this scene.")))
                .thenAnswer(invocation -> {
                    MediaDocumentExtractor.Extraction extraction = invocation.getArgument(0);
                    assertTrue(extraction.text().contains("A scenic illustration."));
                    return "I enjoy this scene.";
                });
        var service = new MediaPersonalityService(new MediaDocumentExtractor(), fetcher,
                new MediaPreprocessor(video -> List.of()), analyzer, imageOpinionService, promptStore,
                new InterestMemoryStore(tempDirectory.resolve("memories.md")));

        var result = service.processImage(null, uri.toString(), "I like this scene.");

        assertEquals(1, result.analyzedMediaCount());
        assertEquals("I enjoy this scene.", promptStore.load());
    }

    @Test
    void uploadsAndPreparesImageFixturesForSupportedFormats() throws Exception {
        var fixtures = List.of(
                new ImageFixture("779224415611256832_0.jpg", "image/jpeg", "image/jpeg"),
                new ImageFixture("774972759111532544_0.png", "image/png", "image/png"),
                new ImageFixture("785559147821367296_0.webp", "image/webp", "image/webp"),
                new ImageFixture("682436876519473152_0.gif", "image/gif", "image/png")
        );
        var imageOpinionService = mock(PersonalityImageOpinionService.class);
        var analyzedImages = new ArrayList<RemoteMediaFetcher.MediaAsset>();
        when(imageOpinionService.analyze(any(), eq("What is this image about?")))
                .thenAnswer(invocation -> {
                    RemoteMediaFetcher.MediaAsset image = invocation.getArgument(0);
                    assertTrue(image.content().length > 0);
                    analyzedImages.add(image);
                    return "An image fixture.";
                });
        for (int index = 0; index < fixtures.size(); index++) {
            ImageFixture fixture = fixtures.get(index);
            byte[] content;
            try (var input = new ClassPathResource(fixture.filename()).getInputStream()) {
                content = input.readAllBytes();
            }
            Path storePath = tempDirectory.resolve("fixture-" + index);
            Files.createDirectories(storePath);
            var promptStore = new PersonalityPromptStore(storePath.resolve("personality.md"));
            var analyzer = mock(PersonalityAnalyzer.class);
            when(analyzer.analyze(any(), eq(List.of()), eq(""), eq(""))).thenReturn("I enjoy this image.");
            var service = new MediaPersonalityService(new MediaDocumentExtractor(),
                    mock(RemoteMediaFetcher.class), new MediaPreprocessor(video -> List.of()),
                    analyzer, imageOpinionService, promptStore,
                    new InterestMemoryStore(storePath.resolve("memories.md")));
            var upload = new MockMultipartFile("file", fixture.filename(), fixture.contentType(), content);

            var result = service.processImage(upload, null, "");

            assertEquals(1, result.analyzedMediaCount(), fixture.filename());
            assertEquals("I enjoy this image.", promptStore.load(), fixture.filename());
            RemoteMediaFetcher.MediaAsset processedImage = analyzedImages.get(index);
            assertEquals(fixture.processedContentType(), processedImage.contentType(), fixture.filename());
            if (fixture.filename().endsWith(".gif")) {
                assertTrue(javax.imageio.ImageIO.read(new ByteArrayInputStream(processedImage.content())) != null);
            } else {
                assertArrayEquals(content, processedImage.content(), fixture.filename());
            }
        }
    }

    private record ImageFixture(String filename, String contentType, String processedContentType) {
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
