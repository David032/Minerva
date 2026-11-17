package com.minerva.core.reminders;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Persisted, single-user reminder state.
 *
 * <p>Scheduled reminders transition to due when their time arrives. Due reminders stay active
 * until acknowledged, allowing clients to recover notifications after a restart or disconnect.</p>
 */
@Entity
@Table(name = "reminders", indexes = {
        @Index(name = "idx_reminders_status_scheduled_at", columnList = "status, scheduled_at")
})
public class Reminder {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 1000)
    private String message;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Column(name = "time_zone", nullable = false, length = 64)
    private String timeZone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReminderStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Reminder() {
    }

    /**
     * Creates a new scheduled reminder.
     *
     * @param message reminder text shown to the user
     * @param scheduledAt absolute instant at which the reminder becomes due
     * @param timeZone zone used to interpret and display the requested local time
     * @param createdAt creation time
     */
    public Reminder(String message, Instant scheduledAt, String timeZone, Instant createdAt) {
        this.message = message;
        this.scheduledAt = scheduledAt;
        this.timeZone = timeZone;
        this.status = ReminderStatus.SCHEDULED;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    /** @return persistent identifier, or {@code null} before the entity is saved */
    public UUID getId() {
        return id;
    }

    /** @return reminder text */
    public String getMessage() {
        return message;
    }

    /** @return the scheduled absolute instant */
    public Instant getScheduledAt() {
        return scheduledAt;
    }

    /** @return time zone used to interpret and display the scheduled time */
    public String getTimeZone() {
        return timeZone;
    }

    /** @return current lifecycle status */
    public ReminderStatus getStatus() {
        return status;
    }

    /** @return creation time */
    public Instant getCreatedAt() {
        return createdAt;
    }

    /** @return time of the most recent lifecycle transition */
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Marks this reminder due if it is still scheduled.
     *
     * @param now time at which the transition is recorded
     */
    void markDue(Instant now) {
        if (status == ReminderStatus.SCHEDULED) {
            status = ReminderStatus.DUE;
            updatedAt = now;
        }
    }

    /**
     * Cancels this reminder unless it has already been acknowledged.
     *
     * @param now time at which cancellation is recorded
     * @throws IllegalStateException if the reminder was acknowledged
     */
    void cancel(Instant now) {
        if (status == ReminderStatus.ACKNOWLEDGED) {
            throw new IllegalStateException("An acknowledged reminder cannot be cancelled");
        }
        if (status != ReminderStatus.CANCELLED) {
            status = ReminderStatus.CANCELLED;
            updatedAt = now;
        }
    }

    /**
     * Acknowledges a due reminder.
     *
     * @param now time at which acknowledgement is recorded
     * @throws IllegalStateException if the reminder is not due
     */
    void acknowledge(Instant now) {
        if (status == ReminderStatus.ACKNOWLEDGED) {
            return;
        }
        if (status != ReminderStatus.DUE) {
            throw new IllegalStateException("Only a due reminder can be acknowledged");
        }
        status = ReminderStatus.ACKNOWLEDGED;
        updatedAt = now;
    }
}
