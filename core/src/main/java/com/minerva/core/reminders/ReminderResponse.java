package com.minerva.core.reminders;

import java.time.Instant;
import java.util.UUID;

/**
 * API representation of a reminder.
 *
 * @param id reminder identifier
 * @param message reminder text
 * @param scheduledAt absolute scheduled instant
 * @param timeZone zone used to interpret and display the scheduled time
 * @param status current lifecycle status
 * @param createdAt creation time
 * @param updatedAt last lifecycle transition time
 */
public record ReminderResponse(
        UUID id,
        String message,
        Instant scheduledAt,
        String timeZone,
        ReminderStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    /**
     * Creates an API response from a persisted reminder.
     *
     * @param reminder persisted reminder
     * @return immutable response value
     */
    static ReminderResponse from(Reminder reminder) {
        return new ReminderResponse(
                reminder.getId(),
                reminder.getMessage(),
                reminder.getScheduledAt(),
                reminder.getTimeZone(),
                reminder.getStatus(),
                reminder.getCreatedAt(),
                reminder.getUpdatedAt()
        );
    }
}
