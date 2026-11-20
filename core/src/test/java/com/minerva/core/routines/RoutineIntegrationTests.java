package com.minerva.core.routines;

import com.minerva.core.MinervaTestConfiguration;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Import(MinervaTestConfiguration.class)
@Tag("ContinuousIntegration")
class RoutineIntegrationTests {
    @Autowired RoutineService service;
    @Autowired EntityManager entityManager;

    @Test
    @Transactional
    void definitionsAndOutcomesSurviveReloadAndDisabledRoutinesAreSkipped() {
        var created = service.create("Check status", "Check the system");
        entityManager.flush();
        entityManager.clear();
        assertEquals("Check the system", service.get(created.getId()).getInstructions());
        var attempt = service.begin();
        assertEquals(created.getId(), attempt.routineId());
        service.finish(attempt.runId(), "FAILED", "Unavailable");
        service.disable(created.getId());
        entityManager.flush();
        entityManager.clear();
        assertFalse(service.get(created.getId()).isEnabled());
        assertEquals("FAILED", service.history(created.getId()).getFirst().getStatus());
        assertEquals("Unavailable", service.history(created.getId()).getFirst().getOutcome());
        assertNull(service.begin());
    }
}
