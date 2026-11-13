package com.minerva.api.personality;

import com.minerva.core.personality.PersonalityPromptStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@Tag("ContinuousIntegration")
class MediaPersonalityWorkflowIntegrationTests {
    private static final List<String> SAMPLE_PAGES = List.of(
            "639943991806279680.html",
            "640880051192217600.html",
            "731717751510581249.html",
            "806031500234620928.html"
    );
    private static final List<ImageFixture> IMAGE_FIXTURES = List.of(
            new ImageFixture("779224415611256832_0.jpg", "image/jpeg", "image/jpeg"),
            new ImageFixture("774972759111532544_0.png", "image/png", "image/png"),
            new ImageFixture("785559147821367296_0.webp", "image/webp", "image/webp"),
            new ImageFixture("682436876519473152_0.gif", "image/gif", "image/png")
    );
    private static final String VIDEO_FIXTURE = "731543319053778944_0.mp4";

    @TempDir
    Path tempDirectory;

    private MockMvc mockMvc;
    private PersonalityPromptStore promptStore;
    private InterestMemoryStore memoryStore;
    private MediaDocumentExtractor extractor;
    private RemoteMediaFetcher fetcher;
    private VideoFrameExtractor videoFrameExtractor;
    private PersonalityAnalyzer analyzer;
    private PersonalityImageOpinionService imageOpinionService;
    private List<java.net.URI> fetchedMedia;
    private List<RemoteMediaFetcher.MediaAsset> analyzedMedia;
    private RemoteMediaFetcher.MediaAsset imageSentForOpinion;

    @BeforeEach
    void setUp() throws Exception {
        promptStore = new PersonalityPromptStore(tempDirectory.resolve("personality.md"));
        memoryStore = new InterestMemoryStore(tempDirectory.resolve("memories.md"));
        extractor = new MediaDocumentExtractor();
        fetchedMedia = new ArrayList<>();

        fetcher = mock(RemoteMediaFetcher.class);
        when(fetcher.fetch(anyList())).thenAnswer(invocation -> {
            List<java.net.URI> references = invocation.getArgument(0);
            if (references.size() > 8) {
                throw new IllegalArgumentException("The HTML file references more than 8 media items.");
            }
            fetchedMedia.addAll(references);
            return references.stream()
                    .map(uri -> new RemoteMediaFetcher.MediaAsset(uri, "image/png", new byte[] {1, 2, 3}))
                    .toList();
        });

        videoFrameExtractor = mock(VideoFrameExtractor.class);
        when(videoFrameExtractor.extract(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(new byte[] {1, 2, 3}));
        analyzer = mock(PersonalityAnalyzer.class);
        analyzedMedia = new ArrayList<>();
        when(analyzer.analyze(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString()
        )).thenAnswer(invocation -> {
            MediaDocumentExtractor.Extraction extraction = invocation.getArgument(0);
            List<RemoteMediaFetcher.MediaAsset> media = invocation.getArgument(1);
            analyzedMedia.clear();
            analyzedMedia.addAll(media);
            String guidance = invocation.getArgument(3);
            return "Updated from " + extraction.tags().size() + " interest tags with guidance: " + guidance;
        });
        imageOpinionService = mock(PersonalityImageOpinionService.class);
        when(imageOpinionService.analyze(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString()
        )).thenAnswer(invocation -> {
            imageSentForOpinion = invocation.getArgument(0);
            return "A description of the uploaded image.";
        });

        var service = new MediaPersonalityService(
                extractor,
                fetcher,
                new MediaPreprocessor(videoFrameExtractor),
                analyzer,
                imageOpinionService,
                promptStore,
                memoryStore
        );
        mockMvc = MockMvcBuilders.standaloneSetup(new PersonalityController(service)).build();
    }

    @Test
    void uploadsAllSamplePagesThroughThePersonalityWorkflow() throws Exception {
        var expectedTags = new LinkedHashSet<String>();
        int expectedMediaCount = 0;
        int lastSuccessfulPageTagCount = 0;

        for (var samplePage : samplePages()) {
            var upload = new MockMultipartFile("file", samplePage.filename(), "text/html", samplePage.html());
            var extracted = extractor.extract(upload);
            String previousPrompt = promptStore.load();
            Path memoriesPath = tempDirectory.resolve("memories.md");
            String previousMemories = Files.exists(memoriesPath) ? Files.readString(memoriesPath) : null;

            var result = mockMvc.perform(multipart("/personality/upload")
                            .file(upload)
                            .param("guidance", "I enjoy this kind of art."))
                    .andExpect(status().isOk())
                    .andExpect(view().name("personality"))
                    .andReturn();

            var resultModel = result.getModelAndView().getModel();
            if (extracted.mediaUrls().size() > 8) {
                assertTrue(resultModel.containsKey("error"), resultModel.toString());
                assertTrue(((String) resultModel.get("error")).contains("more than 8 media items"));
                assertFalse(resultModel.containsKey("success"));
                assertEquals(previousPrompt, promptStore.load());
                String memoriesAfter = Files.exists(memoriesPath) ? Files.readString(memoriesPath) : null;
                assertEquals(previousMemories, memoriesAfter);
            } else {
                expectedTags.addAll(extracted.tags());
                expectedMediaCount += extracted.mediaUrls().size();
                lastSuccessfulPageTagCount = extracted.tags().size();
                assertFalse(resultModel.containsKey("error"), resultModel.toString());
                assertTrue(((String) resultModel.get("success")).contains("Analysis complete"));
                assertTrue(promptStore.load().startsWith("Updated from "));
                assertTrue(promptStore.load().contains("I enjoy this kind of art."));
            }
        }

        assertEquals(expectedMediaCount, fetchedMedia.size());
        assertFalse(fetchedMedia.isEmpty(), "The sample pages should exercise referenced media retrieval.");
        assertTrue(fetchedMedia.stream().allMatch(uri ->
                uri.getScheme().equalsIgnoreCase("https")
                        && (uri.getHost().endsWith("tumblr.com") || uri.getHost().equals("media.example.test"))));

        String persistedMemories = Files.readString(tempDirectory.resolve("memories.md"));
        for (String tag : expectedTags) {
            assertTrue(persistedMemories.contains("- " + tag), "Missing persisted interest tag: " + tag);
        }
        assertTrue(expectedTags.size() >= 10, "The sample pages should cover multiple interest tags.");
        assertTrue(promptStore.load().contains(lastSuccessfulPageTagCount + " interest tags"));
    }

    @Test
    void uploadsSupportedImageFixturesThroughThePersonalityWorkflow() throws Exception {
        for (ImageFixture fixture : IMAGE_FIXTURES) {
            byte[] content = resourceBytes(fixture.filename());
            var result = mockMvc.perform(multipart("/personality/upload")
                            .file(new MockMultipartFile("file", fixture.filename(), fixture.uploadContentType(), content))
                            .param("comment", "I like this image."))
                    .andExpect(status().isOk())
                    .andExpect(view().name("personality"))
                    .andReturn();

            var resultModel = result.getModelAndView().getModel();
            assertFalse(resultModel.containsKey("error"), resultModel.toString());
            assertTrue(((String) resultModel.get("success")).contains("1 media item"));
            assertTrue(promptStore.load().startsWith("Updated from 0 interest tags"));
            assertEquals(fixture.processedContentType(), imageSentForOpinion.contentType(), fixture.filename());
            if (fixture.filename().endsWith(".gif")) {
                assertTrue(javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(imageSentForOpinion.content())) != null);
            } else {
                assertTrue(java.util.Arrays.equals(content, imageSentForOpinion.content()), fixture.filename());
            }
        }
    }

    @Test
    void processesReferencedVideoFramesThroughThePersonalityWorkflow() throws Exception {
        byte[] video = resourceBytes(VIDEO_FIXTURE);
        byte[] extractedFrame = resourceBytes("774972759111532544_0.png");
        var uri = java.net.URI.create("https://media.example.test/" + VIDEO_FIXTURE);
        org.mockito.Mockito.doAnswer(invocation -> List.of(
                new RemoteMediaFetcher.MediaAsset(uri, "video/mp4", video)
        )).when(fetcher).fetch(anyList());
        when(videoFrameExtractor.extract(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(extractedFrame));
        var html = new MockMultipartFile("file", "video.html", "text/html",
                ("<video src=\"" + uri + "\"></video>").getBytes(java.nio.charset.StandardCharsets.UTF_8));

        var result = mockMvc.perform(multipart("/personality/upload")
                        .file(html)
                        .param("guidance", "I enjoy these videos."))
                .andExpect(status().isOk())
                .andExpect(view().name("personality"))
                .andReturn();

        var resultModel = result.getModelAndView().getModel();
        assertFalse(resultModel.containsKey("error"), resultModel.toString());
        assertTrue(promptStore.load().contains("I enjoy these videos."));
        assertEquals(1, analyzedMedia.size());
        assertEquals("image/png", analyzedMedia.getFirst().contentType());
        assertTrue(java.util.Arrays.equals(extractedFrame, analyzedMedia.getFirst().content()));
    }

    private static List<SamplePage> samplePages() throws Exception {
        List<SamplePage> pages = new ArrayList<>();
        for (String filename : SAMPLE_PAGES) {
            var resource = new ClassPathResource(filename);
            if (resource.exists()) {
                try (var input = resource.getInputStream()) {
                    pages.add(new SamplePage(filename, input.readAllBytes()));
                }
            } else {
                pages.add(new SamplePage(filename, fallbackHtml(filename)));
            }
        }
        return List.copyOf(pages);
    }

    private static byte[] fallbackHtml(String filename) {
        StringBuilder html = new StringBuilder("<html><body><p>Example local preference page.</p>");
        List<String> tags = switch (filename) {
            case "639943991806279680.html" -> List.of(
                    "x-men", "krakoa", "marvel", "marvel comics", "the five", "hope summers",
                    "tempest", "proteus", "egg", "elixir", "mutants", "family");
            case "640880051192217600.html" -> List.of(
                    "warhammer", "warhammer age of sigmar", "age of sigmar", "vampires", "soulblight");
            default -> List.of();
        };
        for (String tag : tags) {
            html.append("<span class=\"tag\">").append(tag).append("</span>");
        }
        int imageCount = filename.equals("731717751510581249.html") ? 9 : 1;
        for (int i = 0; i < imageCount; i++) {
            html.append("<img src=\"https://media.example.test/")
                    .append(filename).append("-").append(i).append(".png\">");
        }
        return html.append("</body></html>").toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static byte[] resourceBytes(String filename) throws Exception {
        try (var input = new ClassPathResource(filename).getInputStream()) {
            return input.readAllBytes();
        }
    }

    private record SamplePage(String filename, byte[] html) {
    }

    private record ImageFixture(String filename, String uploadContentType, String processedContentType) {
    }
}
