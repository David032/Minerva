package com.minerva.core.personality;

import com.minerva.core.MinervaTestConfiguration;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Import(MinervaTestConfiguration.class)
@Tag("ContinuousIntegration")
class PersonalityImportJobRepositoryIntegrationTests {
    @Autowired
    private PersonalityImportJobRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    void recoversPersistedInterruptedJobIntoQueuedDispatchQuery() {
        UUID id = UUID.randomUUID();
        var job = new PersonalityImportJob(id, PersonalityImportKind.ZIP,
                "", id.toString(), "[]", Instant.now());
        job.start(Instant.now());
        repository.saveAndFlush(job);
        entityManager.clear();

        var interrupted = repository.findById(id).orElseThrow();
        assertEquals(PersonalityImportJobStatus.RUNNING, interrupted.getStatus());
        interrupted.requeue(Instant.now());
        repository.saveAndFlush(interrupted);
        entityManager.clear();

        var queued = repository.findAllByStatusInOrderByCreatedAtAsc(List.of(PersonalityImportJobStatus.QUEUED));
        assertTrue(queued.stream().anyMatch(candidate -> candidate.getId().equals(id)));
    }

    @Test
    @Transactional
    void persistsJobStateAndAggregateResultsAcrossEntityReloads() {
        UUID id = UUID.randomUUID();
        var job = new PersonalityImportJob(id, PersonalityImportKind.IMAGE_BATCH,
                "I like these.", id.toString(), "[]", Instant.now());
        repository.saveAndFlush(job);
        entityManager.clear();

        var reloaded = repository.findById(id).orElseThrow();
        reloaded.start(Instant.now());
        reloaded.complete(PersonalityImportJobStatus.COMPLETED_WITH_ERRORS, 2, 4, 6,
                "[{\"filename\":\"bad.png\",\"message\":\"Unsupported image.\"}]", null, Instant.now());
        repository.saveAndFlush(reloaded);
        entityManager.clear();

        var persisted = repository.findById(id).orElseThrow();
        assertEquals(PersonalityImportJobStatus.COMPLETED_WITH_ERRORS, persisted.getStatus());
        assertEquals(2, persisted.getSuccessfulFileCount());
        assertEquals(4, persisted.getSavedTagCount());
        assertEquals(6, persisted.getAnalysedMediaCount());
        assertEquals("[{\"filename\":\"bad.png\",\"message\":\"Unsupported image.\"}]", persisted.getFailuresJson());
    }
}
