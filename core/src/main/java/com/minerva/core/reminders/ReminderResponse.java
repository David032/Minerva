package com.minerva.core.reminders;

import java.time.Instant;
import java.util.UUID;

public record ReminderResponse(
        UUID id,
        String message,
        Instant scheduledAt,
        String timeZone,
        ReminderStatus status,
        Instant createdAt,
        Instant updatedAt
) {
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
