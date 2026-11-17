package com.minerva.core.reminders;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class ReminderService {
    private static final List<ReminderStatus> ACTIVE_STATUSES = List.of(
            ReminderStatus.SCHEDULED,
            ReminderStatus.DUE
    );
    private final ReminderRepository repository;
    private final ReminderTimeParser timeParser;
    private final Clock clock;

    public ReminderService(
            ReminderRepository repository,
            ReminderTimeParser timeParser,
            Clock clock,
            @Value("${minerva.reminders.max-message-length:1000}") int maxMessageLength
    ) {
        this.repository = repository;
        this.timeParser = timeParser;
        this.clock = clock;
        this.maxMessageLength = maxMessageLength;
    }

    private final int maxMessageLength;

    @Transactional
    public ReminderResponse create(String time, String message, String timeZone) {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("A reminder message is required");
        }
        String normalizedMessage = message.trim();
        if (normalizedMessage.length() > maxMessageLength) {
            throw new IllegalArgumentException("Reminder messages cannot exceed " + maxMessageLength + " characters");
        }

        var resolvedTime = timeParser.parse(time, timeZone);
        Instant now = clock.instant();
        if (!resolvedTime.instant().isAfter(now)) {
            throw new IllegalArgumentException("A reminder must be scheduled for a future time");
        }
        var reminder = repository.save(new Reminder(
                normalizedMessage,
                resolvedTime.instant(),
                resolvedTime.timeZone(),
                now
        ));
        return ReminderResponse.from(reminder);
    }

    @Transactional(readOnly = true)
    public List<ReminderResponse> listActive() {
        return repository.findByStatusInOrderByScheduledAtAsc(ACTIVE_STATUSES)
                .stream()
                .map(ReminderResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReminderResponse> listAll() {
        return repository.findAllByOrderByScheduledAtAsc()
                .stream()
                .map(ReminderResponse::from)
                .toList();
    }

    @Transactional
    public ReminderResponse cancel(UUID id) {
        Reminder reminder = findReminder(id);
        reminder.cancel(clock.instant());
        return ReminderResponse.from(repository.save(reminder));
    }

    @Transactional
    public ReminderResponse acknowledge(UUID id) {
        Reminder reminder = findReminder(id);
        reminder.acknowledge(clock.instant());
        return ReminderResponse.from(repository.save(reminder));
    }

    @Transactional
    public int markDueReminders() {
        Instant now = clock.instant();
        return repository.markDueBefore(ReminderStatus.SCHEDULED, ReminderStatus.DUE, now);
    }

    private Reminder findReminder(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Reminder not found: " + id));
    }
}
