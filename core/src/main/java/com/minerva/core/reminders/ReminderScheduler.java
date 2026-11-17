package com.minerva.core.reminders;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReminderScheduler {
    private final ReminderService reminderService;

    public ReminderScheduler(ReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @Scheduled(fixedDelayString = "${minerva.reminders.poll-interval:1000}")
    public void markDueReminders() {
        reminderService.markDueReminders();
    }
}
