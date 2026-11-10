package com.minerva.api;

import com.minerva.utils.FileManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

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
    void uploadsImageForAnalysisAndDeletesItAfterward() throws IOException {
        var operations = mock(ConversationController.ConversationOperations.class);
        var fileManager = mock(FileManager.class);
        var image = new MockMultipartFile("image", "photo.jpg", "image/jpeg", new byte[] {1, 2});
        var savedPath = Path.of("uploads", "photo.jpg");
        when(fileManager.UploadFile("photo.jpg", image)).thenReturn(savedPath);
        when(operations.seekImageOpinion(savedPath.toString(), "describe this")).thenReturn("image response");
        var controller = new ConversationController(operations, fileManager);

        assertEquals("image response", controller.image("describe this", image));

        verify(operations).seekImageOpinion(savedPath.toString(), "describe this");
        verify(fileManager).DeleteFile("photo.jpg");
    }

    @Test
    void deletesUploadedImageWhenAnalysisFails() throws IOException {
        var operations = mock(ConversationController.ConversationOperations.class);
        var fileManager = mock(FileManager.class);
        var image = new MockMultipartFile("image", "photo.jpg", "image/jpeg", new byte[] {1, 2});
        var savedPath = Path.of("uploads", "photo.jpg");
        when(fileManager.UploadFile("photo.jpg", image)).thenReturn(savedPath);
        when(operations.seekImageOpinion(any(), eq("describe this"))).thenThrow(new IOException("analysis failed"));
        var controller = new ConversationController(operations, fileManager);

        assertThrows(IOException.class, () -> controller.image("describe this", image));

        verify(fileManager).DeleteFile("photo.jpg");
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
