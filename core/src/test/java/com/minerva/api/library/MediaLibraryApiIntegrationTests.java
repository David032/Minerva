package com.minerva.api.library;

import com.minerva.core.MinervaApplication;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MediaLibraryApiController.class)
@ContextConfiguration(classes = MinervaApplication.class)
@Tag("ContinuousIntegration")
class MediaLibraryApiIntegrationTests {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MediaLibraryService service;

    @MockitoBean
    private JdbcChatMemoryRepository jdbcChatMemoryRepository;

    @Test
    void listsAcceptedLibraryItemsOverHttp() throws Exception {
        when(service.listItems()).thenReturn(List.of(new MediaLibraryItem(
                "00000000-0000-0000-0000-000000000001", "Interesting article",
                "https://example.com/article", "text/plain", "article.txt",
                Instant.parse("2026-01-01T00:00:00Z"), "Relevant to Minerva.")));

        mockMvc.perform(get("/api/library"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Interesting article"))
                .andExpect(jsonPath("$[0].source").value("https://example.com/article"));
    }

    @Test
    void reportsRejectedCandidatesWithoutCreatingLibraryItems() throws Exception {
        when(service.submitUrl("https://example.com/article"))
                .thenReturn(new MediaLibraryService.Submission(false, "Not relevant.", null));

        mockMvc.perform(multipart("/api/library")
                        .param("url", "https://example.com/article"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(false))
                .andExpect(jsonPath("$.item").value(nullValue()));
    }
}
