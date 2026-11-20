package com.minerva.core.routines;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** A persistent instruction eligible for execution on an idle heartbeat tick. */
@Entity
@Table(name = "routines")
public class Routine {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    UUID id;
    @Column(nullable = false, length = 200)
    String name;
    @Column(nullable = false, length = 4000)
    String instructions;
    @Column(nullable = false)
    boolean enabled;
    @Column(nullable = false)
    Instant createdAt;
    Instant lastStartedAt;

    protected Routine() {}

    Routine(String name, String instructions, Instant now) {
        this.name = name;
        this.instructions = instructions;
        this.enabled = true;
        this.createdAt = now;
    }

    /** @return persistent identifier */
    public UUID getId() { return id; }
    /** @return routine name */
    public String getName() { return name; }
    /** @return instructions passed to the assistant */
    public String getInstructions() { return instructions; }
    /** @return whether the routine can be selected */
    public boolean isEnabled() { return enabled; }
    /** @return creation time */
    public Instant getCreatedAt() { return createdAt; }
    /** @return most recent attempt time, or null */
    public Instant getLastStartedAt() { return lastStartedAt; }
}
