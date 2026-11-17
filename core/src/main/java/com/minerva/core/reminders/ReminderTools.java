package com.minerva.core.reminders;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.UUID;

/**
 * Exposes reminder operations as tools callable by the conversational assistant.
 */
@Component
public class ReminderTools {
    private final ReminderService reminderService;

    /**
     * Creates assistant tools backed by persistent reminder operations.
     *
     * @param reminderService reminder lifecycle service
     */
    public ReminderTools(ReminderService reminderService) {
        this.reminderService = reminderService;
    }

    /**
     * Creates a persistent one-time reminder.
     *
     * @param time ISO-8601 date/time in Minerva's configured zone or with an explicit offset
     * @param message reminder text
     * @return confirmation including the scheduled time and reminder identifier
     */
    @Tool(description = "Create a persistent one-time reminder. Provide an ISO-8601 date/time in the user's configured timezone, or include an explicit offset.")
    public String createReminder(String time, String message) {
        var reminder = reminderService.create(time, message, null);
        return "Reminder created for " + reminder.scheduledAt().atZone(ZoneId.of(reminder.timeZone()))
                + " (ID: " + reminder.id() + ")";
    }

    /**
     * Lists scheduled reminders and due reminders awaiting acknowledgement.
     *
     * @return a readable list of active reminders, or an empty-state message
     */
    @Tool(description = "List all active reminders, including reminders that are already due and waiting for acknowledgement.")
    public String listReminders() {
        var reminders = reminderService.listActive();
        if (reminders.isEmpty()) {
            return "There are no active reminders.";
        }
        return reminders.stream()
                .map(reminder -> reminder.id() + ": " + reminder.message() + " at "
                        + reminder.scheduledAt().atZone(ZoneId.of(reminder.timeZone()))
                        + " (" + reminder.status() + ")")
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    /**
     * Cancels a reminder by UUID.
     *
     * @param reminderId UUID string returned when the reminder was created
     * @return cancellation confirmation
     * @throws IllegalArgumentException if the supplied value is not a UUID
     * @throws java.util.NoSuchElementException if the reminder does not exist
     * @throws IllegalStateException if the reminder has already been acknowledged
     */
    @Tool(description = "Cancel a scheduled or due reminder by its UUID.")
    public String cancelReminder(String reminderId) {
        var reminder = reminderService.cancel(UUID.fromString(reminderId));
        return "Reminder cancelled: " + reminder.id();
    }
}
