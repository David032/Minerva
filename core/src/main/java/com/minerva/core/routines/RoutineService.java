package com.minerva.core.routines;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/** Validates and persists routine definitions and attempts. */
@Service
public class RoutineService {
    private final RoutineRepository routines;
    private final RoutineRunRepository runs;
    private final Clock clock;
    private final int maximum;

    /** Creates the routine store; the maximum includes disabled routines. */
    public RoutineService(RoutineRepository routines, RoutineRunRepository runs, Clock clock,
                          @Value("${minerva.routines.max-count:50}") int maximum) {
        if (maximum < 1 || maximum > 1000) throw new IllegalArgumentException("Routine limit must be between 1 and 1000");
        this.routines = routines;
        this.runs = runs;
        this.clock = clock;
        this.maximum = maximum;
    }

    /** Marks unfinished attempts as interrupted after a restart. */
    @PostConstruct
    public void recover() {
        var interrupted = runs.findByStatus("RUNNING");
        for (var run : interrupted) {
            run.status = "INTERRUPTED";
            run.finishedAt = clock.instant();
            run.outcome = "Application stopped before the attempt completed.";
        }
        runs.saveAll(interrupted);
    }

    /** Creates an enabled routine, subject to the configured count and text limits. */
    public synchronized Routine create(String name, String instructions) {
        name = validated(name, 200, "Name");
        instructions = validated(instructions, 4000, "Instructions");
        if (routines.count() >= maximum) throw new IllegalStateException("Routine limit reached");
        return routines.save(new Routine(name, instructions, clock.instant()));
    }

    /** Lists all routines, including disabled ones. */
    public List<Routine> list() { return routines.findAllByOrderByCreatedAtAsc(); }

    /** Retrieves a routine by identifier. */
    public Routine get(UUID id) {
        return routines.findById(id).orElseThrow(() -> new NoSuchElementException("Routine not found: " + id));
    }

    /** Replaces a routine's name, instructions and enabled state. */
    public synchronized Routine update(UUID id, String name, String instructions, boolean enabled) {
        name = validated(name, 200, "Name");
        instructions = validated(instructions, 4000, "Instructions");
        var routine = get(id);
        routine.name = name;
        routine.instructions = instructions;
        routine.enabled = enabled;
        return routines.save(routine);
    }

    /** Disables a routine without deleting its history. */
    public synchronized Routine disable(UUID id) {
        var routine = get(id);
        routine.enabled = false;
        return routines.save(routine);
    }

    /** Returns up to 20 recent attempts for an existing routine. */
    public List<RoutineRun> history(UUID id) {
        get(id);
        return runs.findByRoutineIdOrderByStartedAtDesc(id, PageRequest.of(0, 20));
    }

    /** Selects and records one attempt, or returns null when none is enabled. */
    @Transactional
    public synchronized Attempt begin() {
        var selected = routines.next(PageRequest.of(0, 1));
        if (selected.isEmpty()) return null;
        var routine = selected.getFirst();
        routine.lastStartedAt = clock.instant();
        routines.save(routine);
        var run = runs.saveAndFlush(new RoutineRun(routine.id, routine.lastStartedAt));
        return new Attempt(run.id, routine.id, routine.name, routine.instructions);
    }

    /** Records a completed attempt and bounds the stored outcome. */
    @Transactional
    public void finish(UUID runId, String status, String outcome) {
        var run = runs.findById(runId).orElseThrow();
        run.status = status;
        run.finishedAt = clock.instant();
        run.outcome = outcome == null ? "" : outcome.substring(0, Math.min(4000, outcome.length()));
        runs.save(run);
    }

    private static String validated(String value, int limit, String field) {
        if (value == null || value.isBlank() || value.strip().length() > limit)
            throw new IllegalArgumentException(field + " must contain between 1 and " + limit + " characters");
        return value.strip();
    }

    /** Immutable snapshot of the definition selected for an attempt. */
    public record Attempt(UUID runId, UUID routineId, String name, String instructions) {}
}
