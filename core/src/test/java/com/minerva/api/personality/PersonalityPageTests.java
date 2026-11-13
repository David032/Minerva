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

import static org.mockito.Mockito.when;
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
    private JdbcChatMemoryRepository jdbcChatMemoryRepository;

    @Test
    void rendersTheThymeleafUploadPage() throws Exception {
        when(service.loadPrompt()).thenReturn("I enjoy quiet gardens.");

        mockMvc.perform(get("/personality"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("multipart/form-data")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/personality/upload")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Shape Minerva")))
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
}
