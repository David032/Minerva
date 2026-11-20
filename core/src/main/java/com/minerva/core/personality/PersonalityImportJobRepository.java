package com.minerva.core.personality;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Persistence access for background personality imports.
 */
public interface PersonalityImportJobRepository extends JpaRepository<PersonalityImportJob, UUID> {
    /**
     * Loads jobs that still need to run or recover.
     *
     * @param statuses lifecycle states to include
     * @return matching jobs ordered oldest first
     */
    List<PersonalityImportJob> findAllByStatusInOrderByCreatedAtAsc(
            Collection<PersonalityImportJobStatus> statuses
    );
}
