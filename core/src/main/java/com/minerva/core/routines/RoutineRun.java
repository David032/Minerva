package com.minerva.core.routines;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Durable record of one routine attempt, including interrupted and failed attempts. */
@Entity
@Table(name = "routine_runs", indexes = @Index(name = "idx_routine_runs_history", columnList = "routineId, startedAt"))
public class RoutineRun {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;
    @Column(nullable = false)
    UUID routineId;
    @Column(nullable = false)
    Instant startedAt;
    Instant finishedAt;
    @Column(nullable = false, length = 20)
    String status;
    @Column(length = 4000)
    String outcome;

    protected RoutineRun() {}

    RoutineRun(UUID routineId, Instant startedAt) {
        this.routineId = routineId;
        this.startedAt = startedAt;
        this.status = "RUNNING";
    }

    /** @return attempt identifier */
    public UUID getId() { return id; }
    /** @return routine identifier */
    public UUID getRoutineId() { return routineId; }
    /** @return attempt start time */
    public Instant getStartedAt() { return startedAt; }
    /** @return completion time, or null while running */
    public Instant getFinishedAt() { return finishedAt; }
    /** @return RUNNING, SUCCEEDED, FAILED, TIMED_OUT or INTERRUPTED */
    public String getStatus() { return status; }
    /** @return bounded assistant response or failure description */
    public String getOutcome() { return outcome; }
}
