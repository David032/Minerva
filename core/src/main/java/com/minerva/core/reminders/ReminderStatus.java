package com.minerva.core.reminders;

/**
 * Lifecycle states for a persisted reminder.
 */
public enum ReminderStatus {
    /** The due time has not yet been reached. */
    SCHEDULED,
    /** The reminder is due and remains visible until acknowledged. */
    DUE,
    /** The user has acknowledged the reminder. */
    ACKNOWLEDGED,
    /** The user cancelled the reminder before acknowledging it. */
    CANCELLED
}
