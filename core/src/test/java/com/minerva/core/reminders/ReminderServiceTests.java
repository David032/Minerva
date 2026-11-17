package com.minerva.core.reminders;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("Unit")
class ReminderServiceTests {
    private static final Instant NOW = Instant.parse("2026-11-17T10:00:00Z");
    private final ReminderRepository repository = mock(ReminderRepository.class);
    private final ReminderService service = new ReminderService(
            repository,
            new ReminderTimeParser("UTC"),
            Clock.fixed(NOW, ZoneOffset.UTC),
            1000
    );

    @Test
    void createsReminderWithNormalizedMessageAndConfiguredZone() {
        when(repository.save(any(Reminder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create("2026-11-17T11:00", "  Take a break  ", null);

        assertEquals("Take a break", response.message());
        assertEquals("2026-11-17T11:00:00Z", response.scheduledAt().toString());
        assertEquals("UTC", response.timeZone());
        assertEquals(ReminderStatus.SCHEDULED, response.status());
    }

    @Test
    void rejectsPastTimesAndDoesNotPersistThem() {
        assertThrows(IllegalArgumentException.class,
                () -> service.create("2026-11-17T09:59", "Too late", null));

        verify(repository, never()).save(any(Reminder.class));
    }

    @Test
    void cancelsScheduledReminder() {
        UUID id = UUID.randomUUID();
        Reminder reminder = new Reminder("Cancel me", NOW.plusSeconds(60), "UTC", NOW);
        when(repository.findById(id)).thenReturn(Optional.of(reminder));
        when(repository.save(reminder)).thenReturn(reminder);

        assertEquals(ReminderStatus.CANCELLED, service.cancel(id).status());
    }

    @Test
    void listsOnlyActiveReminders() {
        var reminder = new Reminder("Due item", NOW.plusSeconds(5), "UTC", NOW);
        when(repository.findByStatusInOrderByScheduledAtAsc(any())).thenReturn(java.util.List.of(reminder));

        var reminders = service.listActive();

        assertEquals(1, reminders.size());
        assertEquals("Due item", reminders.getFirst().message());
    }

    @Test
    void acknowledgesDueReminderAndRejectsScheduledReminder() {
        UUID dueId = UUID.randomUUID();
        Reminder dueReminder = new Reminder("Due", NOW.minusSeconds(1), "UTC", NOW.minusSeconds(2));
        when(repository.findById(dueId)).thenReturn(Optional.of(dueReminder));
        when(repository.save(dueReminder)).thenReturn(dueReminder);
        dueReminder.markDue(NOW.minusSeconds(1));

        assertEquals(ReminderStatus.ACKNOWLEDGED, service.acknowledge(dueId).status());

        UUID scheduledId = UUID.randomUUID();
        when(repository.findById(scheduledId))
                .thenReturn(Optional.of(new Reminder("Later", NOW.plusSeconds(60), "UTC", NOW)));
        assertThrows(IllegalStateException.class, () -> service.acknowledge(scheduledId));
    }

    @Test
    void marksPersistedRemindersDueUsingInjectedClock() {
        when(repository.markDueBefore(ReminderStatus.SCHEDULED, ReminderStatus.DUE, NOW)).thenReturn(2);

        assertEquals(2, service.markDueReminders());
        verify(repository).markDueBefore(ReminderStatus.SCHEDULED, ReminderStatus.DUE, NOW);
    }
}
