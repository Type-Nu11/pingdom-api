package com.typenull.pingdom.shared.outbox.infrastructure;

import com.typenull.pingdom.shared.outbox.domain.OutboxEvent;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventStatus;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

/**
 * Outbox 운영 조회와 배치 선점·고착 복구를 위한 잠금 query를 제공.
 * 배치 query의 lock timeout -2는 Hibernate의 잠긴 행 건너뛰기 힌트이며 실제 SQL은 DB dialect에 따름.
 */
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {

    boolean existsByDeduplicationKey(String deduplicationKey);

    boolean existsByEventTypeAndAggregateTypeAndAggregateIdAndStatusIn(
            OutboxEventType eventType,
            String aggregateType,
            String aggregateId,
            Collection<OutboxEventStatus> statuses
    );

    long countByStatus(OutboxEventStatus status);

    /** null인 분류 조건은 필터를 생략하며 생성 시각 하한·상한은 각각 포함. 시간 조건 적용 여부는 별도 boolean으로 결정. */
    @Query("""
            SELECT event
            FROM OutboxEvent event
            WHERE (:status IS NULL OR event.status = :status)
              AND (:eventType IS NULL OR event.eventType = :eventType)
              AND (:aggregateType IS NULL OR event.aggregateType = :aggregateType)
              AND (:aggregateId IS NULL OR event.aggregateId = :aggregateId)
              AND (:hasFrom = false OR event.createdAt >= :from)
              AND (:hasTo = false OR event.createdAt <= :to)
            """)
    Page<OutboxEvent> findByFilters(
            @Param("status") OutboxEventStatus status,
            @Param("eventType") OutboxEventType eventType,
            @Param("aggregateType") String aggregateType,
            @Param("aggregateId") String aggregateId,
            @Param("hasFrom") boolean hasFrom,
            @Param("from") LocalDateTime from,
            @Param("hasTo") boolean hasTo,
            @Param("to") LocalDateTime to,
            Pageable pageable
    );

    /** 수동 재시도 대상 한 행을 쓰기 잠금으로 조회. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT event
            FROM OutboxEvent event
            WHERE event.eventId = :eventId
            """)
    Optional<OutboxEvent> findByEventIdForUpdate(@Param("eventId") String eventId);

    /** 지정 상태 중 nextAttemptAt이 현재 시각 이하인 행을 생성 시각·ID 순서로 제한 선점. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@jakarta.persistence.QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            SELECT event
            FROM OutboxEvent event
            WHERE event.status IN :statuses
              AND event.nextAttemptAt <= :now
            ORDER BY event.createdAt, event.eventId
            """)
    List<OutboxEvent> findReadyEventsForUpdate(
            @Param("statuses") Collection<OutboxEventStatus> statuses,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );

    /** 처리 시작 시각이 임계값보다 엄격히 이전인 지정 상태의 행을 오래된 순서로 잠금 조회. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@jakarta.persistence.QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            SELECT event
            FROM OutboxEvent event
            WHERE event.status = :status
              AND event.processingStartedAt < :threshold
            ORDER BY event.processingStartedAt
            """)
    List<OutboxEvent> findStaleProcessingEventsForUpdate(
            @Param("status") OutboxEventStatus status,
            @Param("threshold") LocalDateTime threshold,
            Pageable pageable
    );

    /** 처리 완료 시각이 기준보다 이전인 지정 상태의 ID를 오래된 순서·ID 순서로 제한 조회. */
    @Query("""
            SELECT event.eventId
            FROM OutboxEvent event
            WHERE event.status = :status
              AND event.processedAt < :threshold
            ORDER BY event.processedAt, event.eventId
            """)
    List<String> findProcessedEventIdsBefore(
            @Param("status") OutboxEventStatus status,
            @Param("threshold") LocalDateTime threshold,
            Pageable pageable
    );
}
