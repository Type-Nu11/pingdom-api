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

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {

    boolean existsByDeduplicationKey(String deduplicationKey);

    boolean existsByEventTypeAndAggregateTypeAndAggregateIdAndStatusIn(
            OutboxEventType eventType,
            String aggregateType,
            String aggregateId,
            Collection<OutboxEventStatus> statuses
    );

    long countByStatus(OutboxEventStatus status);

    @Query("""
            SELECT event
            FROM OutboxEvent event
            WHERE (:status IS NULL OR event.status = :status)
              AND (:eventType IS NULL OR event.eventType = :eventType)
              AND (:aggregateType IS NULL OR event.aggregateType = :aggregateType)
              AND (:aggregateId IS NULL OR event.aggregateId = :aggregateId)
              AND (:from IS NULL OR event.createdAt >= :from)
              AND (:to IS NULL OR event.createdAt <= :to)
            """)
    Page<OutboxEvent> findByFilters(
            @Param("status") OutboxEventStatus status,
            @Param("eventType") OutboxEventType eventType,
            @Param("aggregateType") String aggregateType,
            @Param("aggregateId") String aggregateId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT event
            FROM OutboxEvent event
            WHERE event.eventId = :eventId
            """)
    Optional<OutboxEvent> findByEventIdForUpdate(@Param("eventId") String eventId);

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
