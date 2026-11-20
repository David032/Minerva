package com.minerva.core.routines;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

/** Database access for routine execution history. */
public interface RoutineRunRepository extends JpaRepository<RoutineRun, UUID> {
    /** Returns the most recent attempts for a routine. */
    List<RoutineRun> findByRoutineIdOrderByStartedAtDesc(UUID routineId, Pageable page);
    /** Returns attempts left running by an earlier application process. */
    List<RoutineRun> findByStatus(String status);
}
