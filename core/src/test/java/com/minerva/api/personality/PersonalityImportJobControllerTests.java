package com.minerva.api.personality;

import com.minerva.core.personality.PersonalityImportJobStatus;
import com.minerva.core.personality.PersonalityImportKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("Unit")
class PersonalityImportJobControllerTests {
    private final PersonalityImportJobService service = mock(PersonalityImportJobService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PersonalityImportJobController(service)).build();
    }

    @Test
    void acceptsAnImageBatchAndReturnsTheStatusLocation() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.submit(eq(PersonalityImportKind.IMAGE_BATCH), anyList(), eq("I like these.")))
                .thenReturn(response(id, PersonalityImportJobStatus.QUEUED));

        mockMvc.perform(multipart("/api/personality/imports")
                        .file(new MockMultipartFile("imageFiles", "photo.png", "image/png", new byte[] {1}))
                        .param("guidance", "I like these."))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/api/personality/imports/" + id))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.status").value("QUEUED"));
    }

    @Test
    void rejectsRequestsContainingMoreThanOneImportType() throws Exception {
        mockMvc.perform(multipart("/api/personality/imports")
                        .file(new MockMultipartFile("htmlFiles", "page.html", "text/html", new byte[] {1}))
                        .file(new MockMultipartFile("zipFile", "pages.zip", "application/zip", new byte[] {1})))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(
                        "Submit exactly one HTML batch, image batch, or ZIP archive."));
    }

    @Test
    void returnsPersistedJobStatusAndPerFileFailures() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.get(id)).thenReturn(new PersonalityImportJobResponse(
                id, PersonalityImportKind.ZIP, PersonalityImportJobStatus.COMPLETED_WITH_ERRORS,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:01:00Z"),
                1, 2, 3,
                List.of(new MediaPersonalityService.FileFailure("notes.txt", "Unsupported file.")), null));

        mockMvc.perform(get("/api/personality/imports/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED_WITH_ERRORS"))
                .andExpect(jsonPath("$.successfulFileCount").value(1))
                .andExpect(jsonPath("$.failures[0].filename").value("notes.txt"));
    }

    private static PersonalityImportJobResponse response(UUID id, PersonalityImportJobStatus status) {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        return new PersonalityImportJobResponse(id, PersonalityImportKind.IMAGE_BATCH, status, now, now,
                0, 0, 0, List.of(), null);
    }
}
