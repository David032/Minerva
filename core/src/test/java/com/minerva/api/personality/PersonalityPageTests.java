package com.minerva.api.personality;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;

import java.io.IOException;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PersonalityController.class)
@ContextConfiguration(classes = com.minerva.core.MinervaApplication.class)
@Tag("Unit")
class PersonalityPageTests {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MediaPersonalityService service;

    @MockitoBean
    private RemoteArticleFetcher articleFetcher;

    @MockitoBean
    private JdbcChatMemoryRepository jdbcChatMemoryRepository;

    @Test
    void rendersTheThymeleafUploadPage() throws Exception {
        when(service.loadPrompt()).thenReturn("I enjoy quiet gardens.");

        mockMvc.perform(get("/personality"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("multipart/form-data")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/personality/upload")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Shape Minerva")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("imageUrl")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"imageFiles\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"articleUrl\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"articleComment\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Optional image comment")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("maximum of 50 files")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("I enjoy quiet gardens.")));
    }

    @Test
    void displaysUploadFailuresWithoutReportingSuccess() throws Exception {
        when(service.loadPrompt()).thenReturn("");
        when(service.process(any(), any()))
                .thenThrow(new IOException("Could not retrieve referenced media."));

        mockMvc.perform(multipart("/personality/upload")
                        .file(new org.springframework.mock.web.MockMultipartFile(
                                "file", "page.html", "text/html", "<p>Example</p>".getBytes()))
                        .param("guidance", "I like this"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Could not retrieve referenced media.")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Analysis complete"))));
    }

    @Test
    void reportsSuccessfulSubmission() throws Exception {
        when(service.process(any(), eq("I like this"))).thenReturn(
                new MediaPersonalityService.SubmissionResult(1, 2, "I enjoy botanical art."));

        mockMvc.perform(multipart("/personality/upload")
                        .file(new org.springframework.mock.web.MockMultipartFile(
                                "file", "page.html", "text/html", "<p>Example</p>".getBytes()))
                        .param("guidance", "I like this"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Analysis complete")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("I enjoy botanical art.")));
    }

    @Test
    void routesImageLinksAndCommentsToImageAnalysis() throws Exception {
        when(service.processImage(org.mockito.ArgumentMatchers.isNull(),
                eq("https://images.example.test/photo.png"), eq("I like this.")))
                .thenReturn(new MediaPersonalityService.SubmissionResult(0, 1, "I enjoy this image."));

        mockMvc.perform(multipart("/personality/upload")
                        .param("imageUrl", "https://images.example.test/photo.png")
                        .param("comment", "I like this."))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("I enjoy this image.")));

        verify(service).processImage(org.mockito.ArgumentMatchers.isNull(),
                eq("https://images.example.test/photo.png"), eq("I like this."));
    }

    @Test
    void routesArticleLinksAndCommentsToPreferenceAnalysis() throws Exception {
        when(articleFetcher.fetch("https://news.example.test/story"))
                .thenReturn(new RemoteArticleFetcher.Article(
                        java.net.URI.create("https://news.example.test/story"), "A story", "Article text"));
        when(service.processArticle(any(), eq("I liked the thoughtful analysis."))).thenReturn(
                new MediaPersonalityService.SubmissionResult(0, 0, "I enjoy thoughtful analysis."));

        mockMvc.perform(multipart("/personality/upload")
                        .param("articleUrl", "https://news.example.test/story")
                        .param("articleComment", "I liked the thoughtful analysis."))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Article analysis complete")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("I enjoy thoughtful analysis.")));

        verify(articleFetcher).fetch("https://news.example.test/story");
        verify(service).processArticle(any(), eq("I liked the thoughtful analysis."));
    }

    @Test
    void routesUploadedImagesAndCommentsToImageAnalysis() throws Exception {
        when(service.processImage(any(), org.mockito.ArgumentMatchers.isNull(), eq("Describe this image.")))
                .thenReturn(new MediaPersonalityService.SubmissionResult(0, 1, "I enjoy this image."));

        mockMvc.perform(multipart("/personality/upload")
                        .file(new org.springframework.mock.web.MockMultipartFile(
                                "file", "photo.png", "image/png", new byte[] {1, 2, 3}))
                        .param("comment", "Describe this image."))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("I enjoy this image.")));

        verify(service).processImage(any(), org.mockito.ArgumentMatchers.isNull(), eq("Describe this image."));
    }

    @Test
    void processesMultipleHtmlFilesAndDisplaysEachFailedFilename() throws Exception {
        when(service.processHtmlBatch(any(), eq("Shared guidance"))).thenReturn(
                new MediaPersonalityService.BatchSubmissionResult(
                        1, 2, 1,
                        List.of(new MediaPersonalityService.FileFailure("broken.html", "No readable HTML content.")),
                        "I enjoy botanical art."));

        mockMvc.perform(multipart("/personality/upload")
                        .file(new org.springframework.mock.web.MockMultipartFile(
                                "htmlFiles", "good.html", "text/html", "<p>Example</p>".getBytes()))
                        .file(new org.springframework.mock.web.MockMultipartFile(
                                "htmlFiles", "broken.html", "text/html", "<p></p>".getBytes()))
                        .param("guidance", "Shared guidance"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Processed 1 HTML file")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("broken.html")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("No readable HTML content.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("I enjoy botanical art.")));
    }

    @Test
    void processesMultipleImageFilesAndDisplaysBatchResults() throws Exception {
        when(service.processImageBatch(any(), eq("Shared comment"))).thenReturn(
                new MediaPersonalityService.BatchSubmissionResult(
                        1, 0, 1,
                        List.of(new MediaPersonalityService.FileFailure("broken.png", "Unsupported image.")),
                        "I enjoy botanical art."));

        mockMvc.perform(multipart("/personality/upload")
                        .file(new org.springframework.mock.web.MockMultipartFile(
                                "imageFiles", "good.png", "image/png", new byte[] {1}))
                        .file(new org.springframework.mock.web.MockMultipartFile(
                                "imageFiles", "broken.png", "image/png", new byte[] {2}))
                        .param("comment", "Shared comment"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Processed 1 image(s)")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("broken.png")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("I enjoy botanical art.")));

        verify(service).processImageBatch(any(), eq("Shared comment"));
    }

    @Test
    void rejectsMixedHtmlAndImageBatches() throws Exception {
        when(service.loadPrompt()).thenReturn("");

        mockMvc.perform(multipart("/personality/upload")
                        .file(new org.springframework.mock.web.MockMultipartFile(
                                "htmlFiles", "page.html", "text/html", "<p>Example</p>".getBytes()))
                        .file(new org.springframework.mock.web.MockMultipartFile(
                                "imageFiles", "photo.png", "image/png", new byte[] {1})))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Submit either an HTML batch or an image batch")));
    }

    @Test
    void rendersAndValidatesScoredInterestPreferences() throws Exception {
        when(service.loadScoredInterests()).thenReturn(
                List.of(new InterestMemoryStore.ScoredInterest("Botanical art", 4)));
        when(service.saveScoredInterest("Botanical art", 0))
                .thenThrow(new IllegalArgumentException("Interest scores must be from 1 to 5."));

        mockMvc.perform(get("/personality/interests"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("1 (strongly negative)")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("5 (strongly positive)")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Botanical art")));

        mockMvc.perform(multipart("/personality/interests")
                        .param("keyword", "Botanical art")
                        .param("score", "0"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Interest scores must be from 1 to 5.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Botanical art")));

        mockMvc.perform(multipart("/personality/interests")
                        .param("keyword", "Botanical art")
                        .param("score", "five"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Interest scores must be whole numbers from 1 to 5.")));
    }
}
