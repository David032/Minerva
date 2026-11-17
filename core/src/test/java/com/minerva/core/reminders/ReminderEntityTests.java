package com.minerva.core.reminders;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("Unit")
class ReminderEntityTests {
    private static final Instant NOW = Instant.parse("2026-11-17T10:00:00Z");

    @Test
    void movesFromScheduledToDueAndAcknowledged() {
        var reminder = new Reminder("Test", NOW, "UTC", NOW.minusSeconds(60));

        reminder.markDue(NOW);
        reminder.acknowledge(NOW.plusSeconds(1));

        assertEquals(ReminderStatus.ACKNOWLEDGED, reminder.getStatus());
        assertEquals(NOW.plusSeconds(1), reminder.getUpdatedAt());
    }

    @Test
    void onlyDueRemindersCanBeAcknowledgedAndAcknowledgedRemindersCannotBeCancelled() {
        var reminder = new Reminder("Test", NOW, "UTC", NOW);

        assertThrows(IllegalStateException.class, () -> reminder.acknowledge(NOW));
        reminder.markDue(NOW);
        reminder.acknowledge(NOW.plusSeconds(1));
        assertThrows(IllegalStateException.class, () -> reminder.cancel(NOW.plusSeconds(2)));
    }
}
