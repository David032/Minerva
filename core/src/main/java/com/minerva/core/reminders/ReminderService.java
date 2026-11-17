package com.minerva.core.reminders;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Applies reminder validation and lifecycle rules over persistent reminder records.
 */
@Service
public class ReminderService {
    private static final List<ReminderStatus> ACTIVE_STATUSES = List.of(
            ReminderStatus.SCHEDULED,
            ReminderStatus.DUE
    );
    private final ReminderRepository repository;
    private final ReminderTimeParser timeParser;
    private final Clock clock;

    /**
     * Creates the service using an injectable clock for deterministic time handling.
     *
     * @param repository reminder persistence
     * @param timeParser date/time and time-zone parser
     * @param clock clock used for validation and lifecycle timestamps
     * @param maxMessageLength configured maximum reminder text length
     */
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

    /**
     * Creates a one-time reminder for a future date and time.
     *
     * @param time ISO-8601 local date/time or date/time with an explicit offset or zone
     * @param message non-blank reminder text within the configured maximum length
     * @param timeZone optional IANA zone for a local date/time without an explicit zone
     * @return the newly persisted reminder
     * @throws IllegalArgumentException if the message or time is invalid, or the time is not in the future
     */
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

    /**
     * Lists scheduled and due reminders, including due items awaiting acknowledgement.
     *
     * @return active reminders ordered by scheduled time
     */
    @Transactional(readOnly = true)
    public List<ReminderResponse> listActive() {
        return repository.findByStatusInOrderByScheduledAtAsc(ACTIVE_STATUSES)
                .stream()
                .map(ReminderResponse::from)
                .toList();
    }

    /**
     * Lists reminders in every lifecycle state.
     *
     * @return all reminders ordered by scheduled time
     */
    @Transactional(readOnly = true)
    public List<ReminderResponse> listAll() {
        return repository.findAllByOrderByScheduledAtAsc()
                .stream()
                .map(ReminderResponse::from)
                .toList();
    }

    /**
     * Cancels a scheduled or due reminder.
     *
     * @param id reminder identifier
     * @return the updated reminder
     * @throws NoSuchElementException if no reminder has the given identifier
     * @throws IllegalStateException if the reminder has already been acknowledged
     */
    @Transactional
    public ReminderResponse cancel(UUID id) {
        Reminder reminder = findReminder(id);
        reminder.cancel(clock.instant());
        return ReminderResponse.from(repository.save(reminder));
    }

    /**
     * Acknowledges a due reminder.
     *
     * @param id reminder identifier
     * @return the updated reminder
     * @throws NoSuchElementException if no reminder has the given identifier
     * @throws IllegalStateException if the reminder is not due
     */
    @Transactional
    public ReminderResponse acknowledge(UUID id) {
        Reminder reminder = findReminder(id);
        reminder.acknowledge(clock.instant());
        return ReminderResponse.from(repository.save(reminder));
    }

    /**
     * Transitions all persisted scheduled reminders that have reached their due time.
     *
     * @return number of reminders transitioned
     */
    @Transactional
    public int markDueReminders() {
        Instant now = clock.instant();
        return repository.markDueBefore(ReminderStatus.SCHEDULED, ReminderStatus.DUE, now);
    }

    /**
     * Loads a reminder by identifier.
     *
     * @param id reminder identifier
     * @return the persisted reminder
     * @throws NoSuchElementException if no reminder has the given identifier
     */
    private Reminder findReminder(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Reminder not found: " + id));
    }
}
