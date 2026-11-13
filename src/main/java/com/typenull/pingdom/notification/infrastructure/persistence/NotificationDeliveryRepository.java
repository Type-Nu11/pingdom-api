package com.typenull.pingdom.notification.infrastructure.persistence;

import com.typenull.pingdom.notification.domain.NotificationDelivery;
import com.typenull.pingdom.notification.domain.NotificationDeliveryChannel;
import com.typenull.pingdom.notification.domain.NotificationDeliveryStatus;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, Long> {

    /** EMAIL만 원자적으로 기록하여 성공 이력이 늦은 실패에 의해 지워지지 않게 한다. */
    @Modifying
    @Query(value = """
            INSERT INTO notification_delivery (
                channel, status, user_id, outbox_event_id, outbox_event_type, recipient_hash,
                provider_message_id, provider_error_code, error_code, failure_reason,
                retryable, attempt_count, created_at, updated_at
            ) VALUES (
                'EMAIL', CASE WHEN :status = 'RETRY_SCHEDULED' AND :maxAttempts <= 1
                              THEN 'FINAL_FAILED' ELSE :status END,
                :userId, :eventId, :eventType, :recipientHash,
                :messageId, :providerErrorCode, :errorCode, :failureReason,
                :retryable, 1, :now, :now
            )
            ON CONFLICT (outbox_event_id, channel, recipient_hash) DO UPDATE SET
                status = CASE WHEN EXCLUDED.status = 'RETRY_SCHEDULED'
                                   AND notification_delivery.attempt_count + 1 >= :maxAttempts
                              THEN 'FINAL_FAILED' ELSE EXCLUDED.status END,
                user_id = EXCLUDED.user_id,
                outbox_event_type = EXCLUDED.outbox_event_type,
                provider_message_id = EXCLUDED.provider_message_id,
                provider_error_code = EXCLUDED.provider_error_code,
                error_code = EXCLUDED.error_code,
                failure_reason = EXCLUDED.failure_reason,
                retryable = EXCLUDED.retryable,
                attempt_count = notification_delivery.attempt_count + 1,
                updated_at = EXCLUDED.updated_at
            WHERE notification_delivery.status <> 'SUCCEEDED'
              AND (:status = 'SUCCEEDED' OR notification_delivery.error_code IS DISTINCT FROM 'EMAIL_REQUEST_STALE')
            """, nativeQuery = true)
    int upsertEmailDelivery(
            @Param("status") String status,
            @Param("userId") Long userId,
            @Param("eventId") String eventId,
            @Param("eventType") String eventType,
            @Param("recipientHash") String recipientHash,
            @Param("messageId") String messageId,
            @Param("providerErrorCode") String providerErrorCode,
            @Param("errorCode") String errorCode,
            @Param("failureReason") String failureReason,
            @Param("retryable") boolean retryable,
            @Param("maxAttempts") int maxAttempts,
            @Param("now") LocalDateTime now
    );

    Optional<NotificationDelivery> findFirstByOutboxEventIdAndChannelAndNotificationIdIsNotNullOrderByCreatedAtAsc(
            String outboxEventId,
            NotificationDeliveryChannel channel
    );

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
              AND (:hasFrom = false OR delivery.createdAt >= :from)
              AND (:hasTo = false OR delivery.createdAt <= :to)
            """)
    Page<NotificationDelivery> findByFilters(
            @Param("userId") Long userId,
            @Param("channel") NotificationDeliveryChannel channel,
            @Param("status") NotificationDeliveryStatus status,
            @Param("notificationType") String notificationType,
            @Param("outboxEventType") String outboxEventType,
            @Param("hasFrom") boolean hasFrom,
            @Param("from") LocalDateTime from,
            @Param("hasTo") boolean hasTo,
            @Param("to") LocalDateTime to,
            Pageable pageable
    );
}
