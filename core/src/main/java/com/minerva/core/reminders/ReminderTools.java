package com.minerva.core.reminders;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.UUID;

@Component
public class ReminderTools {
    private final ReminderService reminderService;

    public ReminderTools(ReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @Tool(description = "Create a persistent one-time reminder. Provide an ISO-8601 date/time in the user's configured timezone, or include an explicit offset.")
    public String createReminder(String time, String message) {
        var reminder = reminderService.create(time, message, null);
        return "Reminder created for " + reminder.scheduledAt().atZone(ZoneId.of(reminder.timeZone()))
                + " (ID: " + reminder.id() + ")";
    }

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

    @Tool(description = "Cancel a scheduled or due reminder by its UUID.")
    public String cancelReminder(String reminderId) {
        var reminder = reminderService.cancel(UUID.fromString(reminderId));
        return "Reminder cancelled: " + reminder.id();
    }
}
