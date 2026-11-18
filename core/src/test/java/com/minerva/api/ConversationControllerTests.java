package com.minerva.api;

import com.minerva.utils.FileManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.MimeTypeUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("Unit")
class ConversationControllerTests {

    @Test
    void forwardsTextMessagesToConversation() {
        var operations = mock(ConversationController.ConversationOperations.class);
        var controller = new ConversationController(operations, mock(FileManager.class));
        when(operations.sendMessage("hello")).thenReturn("response");

        assertEquals("response", controller.message("hello"));
        verify(operations).sendMessage("hello");
    }

    @Test
    void acceptsMultipartImageUploadAndDeletesItAfterward() throws Exception {
        var operations = mock(ConversationController.ConversationOperations.class);
        var fileManager = mock(FileManager.class);
        var image = new MockMultipartFile("image", "photo.jpg", "image/jpeg",
                new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0});
        var savedPath = Path.of("uploads", "photo.jpg");
        when(fileManager.UploadFile(any(), eq(image))).thenReturn(savedPath);
        when(operations.seekImageOpinion(savedPath.toString(), "describe this", MimeTypeUtils.IMAGE_JPEG))
                .thenReturn("image response");
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ConversationController(operations, fileManager)).build();

        mockMvc.perform(multipart("/image")
                        .file(image)
                        .param("message", "describe this"))
                .andExpect(status().isOk())
                .andExpect(content().string("image response"));

        verify(operations).seekImageOpinion(savedPath.toString(), "describe this", MimeTypeUtils.IMAGE_JPEG);
        verify(fileManager).DeleteFile(any());
    }

    @Test
    void deletesUploadedImageWhenAnalysisFails() throws IOException {
        var operations = mock(ConversationController.ConversationOperations.class);
        var fileManager = mock(FileManager.class);
        var image = new MockMultipartFile("image", "photo.jpg", "image/jpeg",
                new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0});
        var savedPath = Path.of("uploads", "photo.jpg");
        when(fileManager.UploadFile(any(), eq(image))).thenReturn(savedPath);
        when(operations.seekImageOpinion(any(), eq("describe this"), eq(MimeTypeUtils.IMAGE_JPEG)))
                .thenThrow(new IOException("analysis failed"));
        var controller = new ConversationController(operations, fileManager);

        assertThrows(IOException.class, () -> controller.image("describe this", image));

        verify(fileManager).DeleteFile(any());
    }

    @Test
    void rejectsUnsupportedImageBytesBeforeSaving() throws Exception {
        var operations = mock(ConversationController.ConversationOperations.class);
        var fileManager = mock(FileManager.class);
        var image = new MockMultipartFile("image", "photo.png", "image/png", new byte[] {1, 2, 3});
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ConversationController(operations, fileManager)).build();

        mockMvc.perform(multipart("/image").file(image))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("supported JPEG, PNG, GIF, or WebP")));

        org.mockito.Mockito.verifyNoInteractions(fileManager, operations);
    }

    @Test
    void uploadsAudioAndReturnsProcessedFile() throws IOException {
        var operations = mock(ConversationController.ConversationOperations.class);
        var fileManager = mock(FileManager.class);
        var audio = new MockMultipartFile("audio", "voice.wav", "audio/wav", new byte[] {3, 4});
        var savedPath = Path.of("uploads", "voice.wav");
        var processedFile = new File("response.wav");
        when(fileManager.UploadFile("voice.wav", audio)).thenReturn(savedPath);
        when(operations.audioProcessing(savedPath.toString())).thenReturn(processedFile);
        var controller = new ConversationController(operations, fileManager);

        assertEquals(processedFile, controller.audio(audio));

        verify(operations).audioProcessing(savedPath.toString());
    }
}
