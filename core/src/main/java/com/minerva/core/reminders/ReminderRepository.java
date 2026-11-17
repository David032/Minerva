package com.minerva.core.reminders;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ReminderRepository extends JpaRepository<Reminder, UUID> {
    List<Reminder> findByStatusInOrderByScheduledAtAsc(Collection<ReminderStatus> statuses);

    List<Reminder> findAllByOrderByScheduledAtAsc();

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
