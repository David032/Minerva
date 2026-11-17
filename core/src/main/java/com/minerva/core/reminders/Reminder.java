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

    public Reminder(String message, Instant scheduledAt, String timeZone, Instant createdAt) {
        this.message = message;
        this.scheduledAt = scheduledAt;
        this.timeZone = timeZone;
        this.status = ReminderStatus.SCHEDULED;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getMessage() {
        return message;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public ReminderStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    void markDue(Instant now) {
        if (status == ReminderStatus.SCHEDULED) {
            status = ReminderStatus.DUE;
            updatedAt = now;
        }
    }

    void cancel(Instant now) {
        if (status == ReminderStatus.ACKNOWLEDGED) {
            throw new IllegalStateException("An acknowledged reminder cannot be cancelled");
        }
        if (status != ReminderStatus.CANCELLED) {
            status = ReminderStatus.CANCELLED;
            updatedAt = now;
        }
    }

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
