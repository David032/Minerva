package com.minerva.api.skills;

import com.minerva.skills.SkillImportService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SkillPortalController.class)
@ContextConfiguration(classes = com.minerva.core.MinervaApplication.class)
@Tag("Unit")
class SkillPortalPageTests {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SkillImportService service;

    @MockitoBean
    private JdbcChatMemoryRepository jdbcChatMemoryRepository;

    @Test
    void rendersTheSkillImportPage() throws Exception {
        mockMvc.perform(get("/skills"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/skills/upload")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/skills/import")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Import a skill")));
    }

    @Test
    void importsUploadedZipAndReportsTheInstalledSkill() throws Exception {
        when(service.importArchive(any())).thenReturn(List.of("example"));

        mockMvc.perform(multipart("/skills/upload")
                        .file(new MockMultipartFile("file", "example.zip",
                                "application/zip", new byte[] {1, 2, 3})))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Imported skill(s): example")));

        verify(service).importArchive(any());
    }

    @Test
    void displaysRemoteImportErrors() throws Exception {
        when(service.importFromSource("https://example.test/skill.zip"))
                .thenThrow(new IOException("The ZIP URL returned HTTP 404."));

        mockMvc.perform(multipart("/skills/import")
                        .param("sourceUrl", "https://example.test/skill.zip"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("The ZIP URL returned HTTP 404.")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Imported skill(s)"))));
    }
}
