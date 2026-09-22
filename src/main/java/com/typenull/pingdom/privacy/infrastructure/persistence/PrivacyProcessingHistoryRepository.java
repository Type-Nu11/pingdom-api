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

/**
 * 감사 검색은 from/to를 포함하고 보관 기간 정리는 threshold 미만만 선택합니다.
 * 정리 조회는 오래된 시각·ID 순서이며 행 잠금이 없으므로 여러 작업자의 배치 선택이 겹칠 수 있습니다.
 */
public interface PrivacyProcessingHistoryRepository extends JpaRepository<PrivacyProcessingHistory, Long> {

    boolean existsByOutboxEventIdAndSubjectUserId(String outboxEventId, Long subjectUserId);

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
