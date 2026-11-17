package com.minerva.core.reminders;

import com.minerva.core.MinervaTestConfiguration;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Import(MinervaTestConfiguration.class)
@Tag("ContinuousIntegration")
class ReminderIntegrationTests {
    @Autowired
    private ReminderService reminderService;

    @Autowired
    private ReminderRepository reminderRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    void reminderPersistsAndOverdueItemsAreRecoveredAsDue() {
        var created = reminderService.create("2099-01-01T10:00", "Persisted reminder", "UTC");
        entityManager.flush();
        entityManager.clear();

        var reloaded = reminderRepository.findById(created.id()).orElseThrow();
        assertEquals("Persisted reminder", reloaded.getMessage());

        var overdue = reminderRepository.saveAndFlush(
                new Reminder("Missed while offline", Instant.now().minusSeconds(10), "UTC", Instant.now())
        );
        reminderService.markDueReminders();
        entityManager.clear();

        assertEquals(ReminderStatus.DUE, reminderRepository.findById(overdue.getId()).orElseThrow().getStatus());
    }
}
