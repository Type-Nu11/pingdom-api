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
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

/**
 * Outbox 운영 조회와 배치 선점·고착 복구를 위한 잠금 query를 제공.
 * 배치 query의 lock timeout -2는 Hibernate의 잠긴 행 건너뛰기 힌트이며 실제 SQL은 DB dialect에 따름.
 */
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {

    @Query(value = "SELECT clock_timestamp() AT TIME ZONE 'UTC'", nativeQuery = true)
    LocalDateTime databaseNow();

    @Query(value = """
            SELECT * FROM outbox_event
            WHERE event_id = :eventId AND claim_token = :token AND status = 'PROCESSING'
              AND lease_until > (clock_timestamp() AT TIME ZONE 'UTC')
            """, nativeQuery = true)
    Optional<OutboxEvent> findOwnedProcessingEvent(
            @Param("eventId") String eventId, @Param("token") String token);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE outbox_event SET status = 'SUCCEEDED',
                processed_at = (clock_timestamp() AT TIME ZONE 'UTC'),
                updated_at = (clock_timestamp() AT TIME ZONE 'UTC'),
                processing_started_at = NULL, claim_token = NULL, lease_until = NULL,
                last_error = NULL, version = version + 1
            WHERE event_id = :eventId AND claim_token = :token AND status = 'PROCESSING'
              AND lease_until > (clock_timestamp() AT TIME ZONE 'UTC')
            """, nativeQuery = true)
    int completeClaim(@Param("eventId") String eventId, @Param("token") String token);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE outbox_event SET
                status = CASE WHEN attempt_count + 1 >= :maxAttempts THEN 'FAILED' ELSE 'RETRY' END,
                attempt_count = attempt_count + 1,
                next_attempt_at = (clock_timestamp() AT TIME ZONE 'UTC')
                    + CASE WHEN attempt_count + 1 >= :maxAttempts THEN INTERVAL '0 milliseconds'
                           ELSE :delayMillis * INTERVAL '1 millisecond' END,
                updated_at = (clock_timestamp() AT TIME ZONE 'UTC'),
                last_error = :error, processing_started_at = NULL,
                claim_token = NULL, lease_until = NULL, version = version + 1
            WHERE event_id = :eventId AND claim_token = :token AND status = 'PROCESSING'
              AND lease_until > (clock_timestamp() AT TIME ZONE 'UTC')
            """, nativeQuery = true)
    int failClaim(@Param("eventId") String eventId, @Param("token") String token,
            @Param("maxAttempts") int maxAttempts, @Param("delayMillis") long delayMillis,
            @Param("error") String error);

    /** 업무를 시작하지 못한 선점 반환은 실패 예산을 사용하지 않는다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE outbox_event SET status = 'RETRY',
                next_attempt_at = (clock_timestamp() AT TIME ZONE 'UTC'),
                updated_at = (clock_timestamp() AT TIME ZONE 'UTC'),
                processing_started_at = NULL, claim_token = NULL, lease_until = NULL,
                version = version + 1
            WHERE event_id = :eventId AND claim_token = :token AND status = 'PROCESSING'
              AND lease_until > (clock_timestamp() AT TIME ZONE 'UTC')
            """, nativeQuery = true)
    int releaseClaim(@Param("eventId") String eventId, @Param("token") String token);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE outbox_event SET
                lease_until = LEAST(
                    (clock_timestamp() AT TIME ZONE 'UTC') + :leaseMillis * INTERVAL '1 millisecond',
                    processing_started_at + :maxExecutionMillis * INTERVAL '1 millisecond'),
                updated_at = (clock_timestamp() AT TIME ZONE 'UTC'), version = version + 1
            WHERE event_id = :eventId AND claim_token = :token AND status = 'PROCESSING'
              AND lease_until > (clock_timestamp() AT TIME ZONE 'UTC')
              AND processing_started_at + :maxExecutionMillis * INTERVAL '1 millisecond'
                    > (clock_timestamp() AT TIME ZONE 'UTC')
            """, nativeQuery = true)
    int renewClaim(@Param("eventId") String eventId, @Param("token") String token,
            @Param("leaseMillis") long leaseMillis, @Param("maxExecutionMillis") long maxExecutionMillis);

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

    /** 만료 lease 및 전환 전 남은 토큰 없는 선점을 복구한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@jakarta.persistence.QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            SELECT event
            FROM OutboxEvent event
            WHERE event.status = :status
              AND (event.leaseUntil IS NULL OR event.leaseUntil <= :threshold)
            ORDER BY event.leaseUntil, event.eventId
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
