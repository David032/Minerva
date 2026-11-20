package com.minerva.core;

import com.minerva.utils.FileManager;
import com.minerva.core.journiv.JournivService;
import com.minerva.core.routines.RoutineRunner;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

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
    private final RoutineRunner routineRunner = mock(RoutineRunner.class);
    private HeartbeatMemoryStore heartbeatMemoryStore;
    private HeartbeatService service;

    @org.junit.jupiter.api.BeforeEach
    void allowHeartbeat() {
        when(routineRunner.runOne()).thenReturn(true);
        heartbeatMemoryStore = new HeartbeatMemoryStore(temporaryDirectory.resolve("heartbeat-memories.json"));
        service = new HeartbeatService(
                activityGate, fileManager, assistant, journivService, routineRunner, heartbeatMemoryStore);
    }

    @Test
    void skipsTickWhileConversationIsActive() throws IOException {
        Path heartbeatFile = temporaryDirectory.resolve("heartbeat.md");
        Files.writeString(heartbeatFile, "Check something");
        when(fileManager.getHeartbeatFilePath()).thenReturn(heartbeatFile);

        try (var ignored = activityGate.tryBeginConversation().orElseThrow()) {
            assertFalse(service.runIfIdle());
        }

        verify(assistant, never()).run(eq("Check something"), any());
        verify(routineRunner, never()).runOne();
        verify(journivService, never()).record(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void skipsHeartbeatWhenRoutineIsStillRunning() {
        when(routineRunner.runOne()).thenReturn(false);
        assertTrue(service.runIfIdle());
        verify(fileManager, never()).getHeartbeatFilePath();
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
        var saved = heartbeatMemoryStore.loadRecent(1).getFirst();
        assertTrue(saved.status().equals("SUCCEEDED"));
        assertTrue(saved.outcome().equals("Checked the status"));
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
        var saved = heartbeatMemoryStore.loadRecent(1).getFirst();
        assertTrue(saved.status().equals("FAILED"));
        assertTrue(saved.outcome().equals("IllegalStateException"));
    }

    @Test
    void storesHeartbeatHistoryEvenWhenJournivIsDisabled() throws IOException {
        Path heartbeatFile = temporaryDirectory.resolve("heartbeat.md");
        Files.writeString(heartbeatFile, "Check something");
        when(fileManager.getHeartbeatFilePath()).thenReturn(heartbeatFile);
        when(assistant.run(eq("Check something"), any())).thenReturn("Activity recorded locally");
        when(journivService.enabled()).thenReturn(false);

        assertTrue(service.runIfIdle());

        assertTrue(heartbeatMemoryStore.loadRecent(1).getFirst().outcome().contains("Activity recorded locally"));
        verify(journivService).record("heartbeat", "", "Activity recorded locally");
    }

    @Test
    void recordsSuccessfulHeartbeatInJournivWhenLocalHistoryCannotBeWritten() throws IOException {
        Path heartbeatFile = temporaryDirectory.resolve("heartbeat.md");
        Files.writeString(heartbeatFile, "Check something");
        when(fileManager.getHeartbeatFilePath()).thenReturn(heartbeatFile);
        when(assistant.run(eq("Check something"), any())).thenReturn("Activity recorded");

        Path blockingFile = temporaryDirectory.resolve("not-a-directory");
        Files.writeString(blockingFile, "block");
        var failingStore = new HeartbeatMemoryStore(blockingFile.resolve("heartbeat-memories.json"));
        var failingService = new HeartbeatService(
                activityGate, fileManager, assistant, journivService, routineRunner, failingStore);

        assertThrows(IllegalStateException.class, failingService::runIfIdle);

        verify(journivService).record("heartbeat", "", "Activity recorded");
    }
}
