package com.minerva.core;

import com.minerva.utils.FileManager;
import com.minerva.core.journiv.JournivService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("Unit")
@ExtendWith(OutputCaptureExtension.class)
class HeartbeatServiceTests {
    @TempDir
    Path temporaryDirectory;

    private final AiActivityGate activityGate = new AiActivityGate();
    private final FileManager fileManager = mock(FileManager.class);
    private final HeartbeatAssistant assistant = mock(HeartbeatAssistant.class);
    private final JournivService journivService = mock(JournivService.class);
    private final HeartbeatService service = new HeartbeatService(activityGate, fileManager, assistant, journivService);

    @Test
    void skipsTickWhileConversationIsActive() throws IOException {
        Path heartbeatFile = temporaryDirectory.resolve("heartbeat.md");
        Files.writeString(heartbeatFile, "Check something");
        when(fileManager.getHeartbeatFilePath()).thenReturn(heartbeatFile);

        try (var ignored = activityGate.tryBeginConversation().orElseThrow()) {
            assertFalse(service.runIfIdle());
        }

        verify(assistant, never()).run(eq("Check something"), any());
        verify(journivService, never()).record(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void skipsMissingAndEmptyHeartbeatFiles() throws IOException {
        Path heartbeatFile = temporaryDirectory.resolve("heartbeat.md");
        when(fileManager.getHeartbeatFilePath()).thenReturn(heartbeatFile);
        assertTrue(service.runIfIdle());

        Files.writeString(heartbeatFile, " \n ");
        assertTrue(service.runIfIdle());

        verify(assistant, never()).run(org.mockito.ArgumentMatchers.anyString(), any());
        verify(journivService, never()).record(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void runsNonEmptyHeartbeatInstructions(CapturedOutput output) throws IOException {
        Path heartbeatFile = temporaryDirectory.resolve("heartbeat.md");
        Files.writeString(heartbeatFile, "Check the latest system status");
        when(fileManager.getHeartbeatFilePath()).thenReturn(heartbeatFile);
        when(assistant.run(eq("Check the latest system status"), any())).thenReturn("Checked the status");

        assertTrue(service.runIfIdle());

        verify(assistant).run(eq("Check the latest system status"), any());
        verify(journivService).record("heartbeat", "", "Checked the status");
        assertTrue(output.getOut().contains("completed actions=0"));
        assertTrue(output.getOut().contains("response=Checked the status"));
    }

    @Test
    void reportsHeartbeatFileReadFailuresWithoutCallingAssistant() throws IOException {
        Path heartbeatPath = Files.createDirectory(temporaryDirectory.resolve("heartbeat.md"));
        when(fileManager.getHeartbeatFilePath()).thenReturn(heartbeatPath);

        assertTrue(service.runIfIdle());

        verify(assistant, never()).run(org.mockito.ArgumentMatchers.anyString(), any());
    }

    @Test
    void logsFailureAndPropagatesIt(CapturedOutput output) throws IOException {
        Path heartbeatFile = temporaryDirectory.resolve("heartbeat.md");
        Files.writeString(heartbeatFile, "Check something");
        when(fileManager.getHeartbeatFilePath()).thenReturn(heartbeatFile);
        when(assistant.run(eq("Check something"), any())).thenThrow(new IllegalStateException("private details"));

        assertThrows(IllegalStateException.class, service::runIfIdle);
        assertTrue(output.getOut().contains("failed actions=0 type=IllegalStateException"));
        assertFalse(output.getOut().contains("private details"));
    }
}
