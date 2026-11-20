package com.minerva.core.routines;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.UUID;

/** Database access for routines. */
public interface RoutineRepository extends JpaRepository<Routine, UUID> {
    /** Returns enabled routines in least-recently-attempted order. */
    @Query("select r from Routine r where r.enabled = true order by case when r.lastStartedAt is null then 0 else 1 end, r.lastStartedAt, r.createdAt, r.id")
    List<Routine> next(Pageable page);
    /** Returns routines in creation order. */
    List<Routine> findAllByOrderByCreatedAtAsc();
}
