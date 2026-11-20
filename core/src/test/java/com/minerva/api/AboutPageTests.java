package com.minerva.api;

import com.minerva.api.library.MediaLibraryItem;
import com.minerva.api.personality.InterestMemoryStore;
import com.minerva.core.HeartbeatMemoryStore;
import com.minerva.core.personality.AboutProfileService;
import com.minerva.core.personality.CustomizationStore;
import org.junit.jupiter.api.BeforeEach;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AboutController.class)
@ContextConfiguration(classes = com.minerva.core.MinervaApplication.class)
@Tag("Unit")
class AboutPageTests {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AboutProfileService aboutProfileService;

    @MockitoBean
    private CustomizationStore customizationStore;

    @MockitoBean
    private JdbcChatMemoryRepository jdbcChatMemoryRepository;

    @BeforeEach
    void provideAboutData() throws Exception {
        when(customizationStore.load()).thenReturn(CustomizationStore.Preferences.defaults());
        when(aboutProfileService.loadPage()).thenReturn(new AboutProfileService.AboutPage(
                "I enjoy thoughtful gardens.",
                List.of(new InterestMemoryStore.ScoredInterest("Gardens", 5)),
                List.of(new HeartbeatMemoryStore.HeartbeatEntry(
                        Instant.parse("2026-11-20T10:00:00Z").toString(), "SUCCEEDED", 1, "Reviewed a garden article.")),
                List.of(new MediaLibraryItem("id", "Botanical print", null, "image/png", "print.png",
                        Instant.parse("2026-11-20T09:00:00Z"), "Reflects Minerva's style.")),
                false,
                "source-digest",
                "source-digest"));
    }

    @Test
    void displaysGeneratedBiographyAndItsSupportingSources() throws Exception {
        mockMvc.perform(get("/about"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("I enjoy thoughtful gardens.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Gardens (5/5)")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Reviewed a garden article.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Botanical print")));
    }

    @Test
    void savesManualBiographyUsingCurrentSourceFingerprint() throws Exception {
        when(customizationStore.load()).thenReturn(new CustomizationStore.Preferences(
                "forest", "comfortable", "system", "warm", "Old biography", "source-digest"));

        mockMvc.perform(post("/about/biography").param("biography", "An edited biography."))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("About biography saved.")));

        verify(customizationStore).saveBiography("An edited biography.", "source-digest");
    }

    @Test
    void exposesAboutRefreshStateToTheOpenPage() throws Exception {
        mockMvc.perform(get("/api/about"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "\"biographySourceDigest\":\"source-digest\"")));
    }
}
