package com.minerva.core.reminders;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Database access for persistent reminders.
 */
public interface ReminderRepository extends JpaRepository<Reminder, UUID> {
    /**
     * Returns reminders in the requested statuses, ordered by scheduled time.
     *
     * @param statuses lifecycle statuses to include
     * @return matching reminders from earliest to latest
     */
    List<Reminder> findByStatusInOrderByScheduledAtAsc(Collection<ReminderStatus> statuses);

    /**
     * Returns every reminder ordered by scheduled time.
     *
     * @return all persisted reminders
     */
    List<Reminder> findAllByOrderByScheduledAtAsc();

    /**
     * Atomically transitions all reminders whose scheduled time has arrived to due.
     *
     * @param scheduledStatus status of reminders eligible for transition
     * @param dueStatus status to assign to eligible reminders
     * @param now cutoff instant and transition timestamp
     * @return number of reminders transitioned
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Reminder reminder
            set reminder.status = :dueStatus,
                reminder.updatedAt = :now
            where reminder.status = :scheduledStatus
              and reminder.scheduledAt <= :now
            """)
    int markDueBefore(
            @Param("scheduledStatus") ReminderStatus scheduledStatus,
            @Param("dueStatus") ReminderStatus dueStatus,
            @Param("now") Instant now
    );
}
