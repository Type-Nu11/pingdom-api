package com.typenull.pingdom.notification.repository;

import com.typenull.pingdom.notification.domain.NotificationDelivery;
import com.typenull.pingdom.notification.domain.NotificationDeliveryChannel;
import com.typenull.pingdom.notification.domain.NotificationDeliveryStatus;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, Long> {

    @Query("""
            SELECT delivery
            FROM NotificationDelivery delivery
            WHERE delivery.outboxEventId = :outboxEventId
              AND delivery.channel = :channel
              AND (
                    (:recipientHash IS NULL AND delivery.recipientHash IS NULL)
                    OR delivery.recipientHash = :recipientHash
                  )
            """)
    Optional<NotificationDelivery> findDeliveryRecord(
            @Param("outboxEventId") String outboxEventId,
            @Param("channel") NotificationDeliveryChannel channel,
            @Param("recipientHash") String recipientHash
    );

    @Query("""
            SELECT delivery
            FROM NotificationDelivery delivery
            WHERE (:userId IS NULL OR delivery.userId = :userId)
              AND (:channel IS NULL OR delivery.channel = :channel)
              AND (:status IS NULL OR delivery.status = :status)
              AND (:notificationType IS NULL OR delivery.notificationType = :notificationType)
              AND (:outboxEventType IS NULL OR delivery.outboxEventType = :outboxEventType)
              AND (:from IS NULL OR delivery.createdAt >= :from)
              AND (:to IS NULL OR delivery.createdAt <= :to)
            """)
    Page<NotificationDelivery> findByFilters(
            @Param("userId") Long userId,
            @Param("channel") NotificationDeliveryChannel channel,
            @Param("status") NotificationDeliveryStatus status,
            @Param("notificationType") String notificationType,
            @Param("outboxEventType") String outboxEventType,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable
    );
}
