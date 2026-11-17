package com.minerva.core.reminders;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically recovers scheduled reminders whose due time has arrived.
 *
 * <p>Reminder state is persisted; this scheduler does not rely on in-memory timers and safely
 * rechecks overdue reminders after an application restart.</p>
 */
@Component
public class ReminderScheduler {
    private final ReminderService reminderService;

    /**
     * Creates the scheduler that delegates due-state transitions to the reminder service.
     *
     * @param reminderService reminder lifecycle service
     */
    public ReminderScheduler(ReminderService reminderService) {
        this.reminderService = reminderService;
    }

    /**
     * Marks all currently due reminders so clients can present them.
     */
    @Scheduled(fixedDelayString = "${minerva.reminders.poll-interval:1000}")
    public void markDueReminders() {
        reminderService.markDueReminders();
    }
}
