package com.minerva.core.journiv;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Database access to pending Journiv activity summaries. */
public interface JournivEventRepository extends JpaRepository<JournivEvent, UUID> {
    /**
     * Retrieves a bounded batch in activity order.
     *
     * @return pending events
     */
    List<JournivEvent> findTop50ByOrderByOccurredAtAsc();
}
