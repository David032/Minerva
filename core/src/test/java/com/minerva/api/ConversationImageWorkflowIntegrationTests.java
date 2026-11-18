package com.minerva.api;

import com.minerva.utils.FileManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.nio.file.Path;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("ContinuousIntegration")
class ConversationImageWorkflowIntegrationTests {
    @Test
    void forwardsTextAndImageMimeTypeThroughMultipartChatRequest() throws Exception {
        var operations = mock(ConversationController.ConversationOperations.class);
        var fileManager = mock(FileManager.class);
        var image = new MockMultipartFile("image", "garden.png", "image/png", new byte[] {
                (byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0
        });
        when(fileManager.UploadFile(anyString(), eq(image))).thenReturn(Path.of("uploads", "image.png"));
        when(operations.seekImageOpinion(anyString(), eq("Describe this garden."),
                eq(org.springframework.util.MimeTypeUtils.parseMimeType("image/png"))))
                .thenReturn("A green garden.");

        var mockMvc = MockMvcBuilders.standaloneSetup(new ConversationController(operations, fileManager)).build();
        mockMvc.perform(multipart("/image")
                        .file(image)
                        .param("message", "Describe this garden."))
                .andExpect(status().isOk())
                .andExpect(content().string("A green garden."));

        verify(operations).seekImageOpinion(
                Path.of("uploads", "image.png").toString(),
                "Describe this garden.",
                org.springframework.util.MimeTypeUtils.parseMimeType("image/png"));
        verify(fileManager).DeleteFile(anyString());
    }
}
