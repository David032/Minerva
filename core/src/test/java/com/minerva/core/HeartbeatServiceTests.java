package com.minerva.core;

import com.minerva.utils.FileManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("Unit")
class HeartbeatServiceTests {
    @TempDir
    Path temporaryDirectory;

    private final AiActivityGate activityGate = new AiActivityGate();
    private final FileManager fileManager = mock(FileManager.class);
    private final HeartbeatAssistant assistant = mock(HeartbeatAssistant.class);
    private final HeartbeatService service = new HeartbeatService(activityGate, fileManager, assistant);

    @Test
    void skipsTickWhileConversationIsActive() throws IOException {
        Path heartbeatFile = temporaryDirectory.resolve("heartbeat.md");
        Files.writeString(heartbeatFile, "Check something");
        when(fileManager.getHeartbeatFilePath()).thenReturn(heartbeatFile);

        try (var ignored = activityGate.tryBeginConversation().orElseThrow()) {
            assertFalse(service.runIfIdle());
        }

        verify(assistant, never()).run("Check something");
    }

    @Test
    void skipsMissingAndEmptyHeartbeatFiles() throws IOException {
        Path heartbeatFile = temporaryDirectory.resolve("heartbeat.md");
        when(fileManager.getHeartbeatFilePath()).thenReturn(heartbeatFile);
        assertTrue(service.runIfIdle());

        Files.writeString(heartbeatFile, " \n ");
        assertTrue(service.runIfIdle());

        verify(assistant, never()).run(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void runsNonEmptyHeartbeatInstructions() throws IOException {
        Path heartbeatFile = temporaryDirectory.resolve("heartbeat.md");
        Files.writeString(heartbeatFile, "Check the latest system status");
        when(fileManager.getHeartbeatFilePath()).thenReturn(heartbeatFile);

        assertTrue(service.runIfIdle());

        verify(assistant).run("Check the latest system status");
    }

    @Test
    void reportsHeartbeatFileReadFailuresWithoutCallingAssistant() throws IOException {
        Path heartbeatPath = Files.createDirectory(temporaryDirectory.resolve("heartbeat.md"));
        when(fileManager.getHeartbeatFilePath()).thenReturn(heartbeatPath);

        assertTrue(service.runIfIdle());

        verify(assistant, never()).run(org.mockito.ArgumentMatchers.anyString());
    }
}
