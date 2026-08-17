package com.typenull.pingdom.privacy.infrastructure.persistence;

import com.typenull.pingdom.privacy.domain.PrivacyProcessingAction;
import com.typenull.pingdom.privacy.domain.PrivacyProcessingHistory;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PrivacyProcessingHistoryRepository extends JpaRepository<PrivacyProcessingHistory, Long> {

    @Query("""
            SELECT history
            FROM PrivacyProcessingHistory history
            WHERE (:subjectUserId IS NULL OR history.subjectUserId = :subjectUserId)
              AND (:actorUserId IS NULL OR history.actorUserId = :actorUserId)
              AND (:action IS NULL OR history.action = :action)
              AND (:hasFrom = false OR history.createdAt >= :from)
              AND (:hasTo = false OR history.createdAt <= :to)
            """)
    Page<PrivacyProcessingHistory> findByFilters(
            @Param("subjectUserId") Long subjectUserId,
            @Param("actorUserId") Long actorUserId,
            @Param("action") PrivacyProcessingAction action,
            @Param("hasFrom") boolean hasFrom,
            @Param("from") LocalDateTime from,
            @Param("hasTo") boolean hasTo,
            @Param("to") LocalDateTime to,
            Pageable pageable
    );

    @Query("""
            SELECT history.id
            FROM PrivacyProcessingHistory history
            WHERE history.createdAt < :threshold
            ORDER BY history.createdAt ASC, history.id ASC
            """)
    List<Long> findIdsCreatedBefore(@Param("threshold") LocalDateTime threshold, Pageable pageable);
}
