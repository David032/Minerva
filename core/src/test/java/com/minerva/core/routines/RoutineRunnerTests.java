package com.minerva.core.routines;

import com.minerva.core.HeartbeatAssistant;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("Unit")
class RoutineRunnerTests {
    private final RoutineService service = mock(RoutineService.class);
    private final UUID runId = UUID.randomUUID();
    private final RoutineService.Attempt attempt = new RoutineService.Attempt(runId, UUID.randomUUID(), "name", "instructions");

    @Test
    void recordsSuccessAndFailure() {
        when(service.begin()).thenReturn(attempt);
        HeartbeatAssistant assistant = mock(HeartbeatAssistant.class);
        when(assistant.run(anyString(), any())).thenReturn("done").thenThrow(new IllegalStateException("failed"));
        var runner = new RoutineRunner(service, assistant, 1);
        assertTrue(runner.runOne());
        verify(service).finish(runId, "SUCCEEDED", "done");
        assertTrue(runner.runOne());
        verify(service).finish(eq(runId), eq("FAILED"), contains("failed"));
    }

    @Test
    void timeoutDoesNotQueueMoreWorkWhenWorkerIgnoresInterruption() throws Exception {
        when(service.begin()).thenReturn(attempt);
        var release = new CountDownLatch(1);
        var exited = new CountDownLatch(1);
        HeartbeatAssistant assistant = (instructions, run) -> {
            try {
                while (true) {
                    try {
                        release.await();
                        break;
                    } catch (InterruptedException ignored) {
                        // Simulates a tool that does not support cancellation.
                    }
                }
                return "late";
            } finally {
                exited.countDown();
            }
        };
        var runner = new RoutineRunner(service, assistant, 1);
        try {
            assertFalse(runner.runOne());
            verify(service).finish(eq(runId), eq("TIMED_OUT"), anyString());
            assertFalse(runner.runOne());
            verify(service, times(1)).begin();
        } finally {
            release.countDown();
            assertTrue(exited.await(5, TimeUnit.SECONDS));
        }
        verify(service, never()).finish(runId, "SUCCEEDED", "late");
    }
}
