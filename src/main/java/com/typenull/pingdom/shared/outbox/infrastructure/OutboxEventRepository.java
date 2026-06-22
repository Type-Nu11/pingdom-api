package com.typenull.pingdom.shared.outbox.infrastructure;

import com.typenull.pingdom.shared.outbox.domain.OutboxEvent;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {

    boolean existsByDeduplicationKey(String deduplicationKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
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

    @Modifying
    @Query("""
            DELETE FROM OutboxEvent event
            WHERE event.status = :status
              AND event.processedAt < :threshold
            """)
    int deleteProcessedBefore(
            @Param("status") OutboxEventStatus status,
            @Param("threshold") LocalDateTime threshold
    );
}
