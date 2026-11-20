package com.minerva.api;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ChatPageController.class)
@ContextConfiguration(classes = com.minerva.core.MinervaApplication.class)
@Tag("Unit")
class ChatPageTests {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JdbcChatMemoryRepository jdbcChatMemoryRepository;

    @Test
    void rendersTheThymeleafChatPage() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("What can I help you with?")))
                .andExpect(content().string(containsString("id=\"conversation\"")))
                .andExpect(content().string(containsString("id=\"message-form\"")))
                .andExpect(content().string(containsString("id=\"message-input\"")))
                .andExpect(content().string(containsString("id=\"send-button\"")))
                .andExpect(content().string(containsString("id=\"status\"")))
                .andExpect(content().string(containsString("id=\"reminder-form\"")))
                .andExpect(content().string(containsString("id=\"toggle-reminders\"")))
                .andExpect(content().string(containsString("id=\"reminder-list\"")))
                .andExpect(content().string(containsString("id=\"image-input\"")))
                .andExpect(content().string(containsString("id=\"attach-image-button\"")))
                .andExpect(content().string(containsString("id=\"image-preview\"")))
                .andExpect(content().string(containsString("href=\"/personality\"")))
                .andExpect(content().string(containsString("href=\"/library\"")))
                .andExpect(content().string(containsString("href=\"/chat.css\"")))
                .andExpect(content().string(containsString("src=\"/chat.js\"")));
    }

    @Test
    void rendersTheRSSLibraryPageAndFeedForm() throws Exception {
        mockMvc.perform(get("/library"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<title>Library | Minerva</title>")))
                .andExpect(content().string(containsString("href=\"/\"")))
                .andExpect(content().string(containsString("action=\"/rss/addRssFeed\"")))
                .andExpect(content().string(containsString("id=\"media-candidate-form\"")))
                .andExpect(content().string(containsString("id=\"media-library-items\"")))
                .andExpect(content().string(containsString("Manage RSS feeds and the media Minerva chooses to keep.")))
                .andExpect(content().string(containsString("name=\"title\"")))
                .andExpect(content().string(containsString("name=\"url\"")))
                .andExpect(content().string(containsString("id=\"rss-feed-status\"")))
                .andExpect(content().string(containsString("src=\"/library.js\"")));
    }
}
