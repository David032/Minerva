package com.minerva.core.routines;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("Unit")
class RoutineServiceTests {
    private final RoutineRepository routines = mock(RoutineRepository.class);
    private final RoutineRunRepository runs = mock(RoutineRunRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private final RoutineService service = new RoutineService(routines, runs, clock, 1);

    @Test
    void validatesDefinitionsAndLimit() {
        assertThrows(IllegalArgumentException.class, () -> service.create(" ", "instructions"));
        assertThrows(IllegalArgumentException.class, () -> service.create("name", "x".repeat(4001)));
        when(routines.count()).thenReturn(1L);
        assertThrows(IllegalStateException.class, () -> service.create("name", "instructions"));
        verify(routines, never()).save(any());
    }

    @Test
    void updatesAndDisablesWithoutRemovingHistory() {
        var id = UUID.randomUUID();
        var routine = new Routine("old", "old", clock.instant());
        when(routines.findById(id)).thenReturn(Optional.of(routine));
        when(routines.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service.update(id, " name ", " instructions ", true);
        assertEquals("name", routine.getName());
        assertEquals("instructions", routine.getInstructions());
        service.disable(id);
        assertFalse(routine.isEnabled());
        verify(runs, never()).deleteAll();
    }

    @Test
    void recordsAttemptsAndRecoversInterruptedRuns() {
        var id = UUID.randomUUID();
        var runId = UUID.randomUUID();
        var routine = new Routine("name", "instructions", clock.instant());
        routine.id = id;
        when(routines.next(any(Pageable.class))).thenReturn(List.of(routine));
        when(runs.saveAndFlush(any())).thenAnswer(invocation -> {
            RoutineRun run = invocation.getArgument(0);
            run.id = runId;
            return run;
        });
        var attempt = service.begin();
        assertEquals(id, attempt.routineId());
        assertEquals(clock.instant(), routine.getLastStartedAt());

        var interrupted = new RoutineRun(id, clock.instant());
        when(runs.findByStatus("RUNNING")).thenReturn(List.of(interrupted));
        service.recover();
        assertEquals("INTERRUPTED", interrupted.getStatus());
        assertEquals(clock.instant(), interrupted.getFinishedAt());

        when(runs.findById(runId)).thenReturn(Optional.of(interrupted));
        service.finish(runId, "FAILED", "x".repeat(5000));
        assertEquals("FAILED", interrupted.getStatus());
        assertEquals(4000, interrupted.getOutcome().length());
    }
}
