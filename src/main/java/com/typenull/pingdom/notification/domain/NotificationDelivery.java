package com.typenull.pingdom.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "notification_delivery",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_notification_delivery_outbox_channel_recipient",
                        columnNames = {"outbox_event_id", "channel", "recipient_hash"}
                )
        },
        indexes = {
                @Index(name = "idx_notification_delivery_user_created", columnList = "user_id, created_at"),
                @Index(name = "idx_notification_delivery_channel_status_created", columnList = "channel, status, created_at"),
                @Index(name = "idx_notification_delivery_outbox_event", columnList = "outbox_event_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationDelivery {

    private static final int FAILURE_REASON_MAX_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", length = 20, nullable = false, updatable = false)
    private NotificationDeliveryChannel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private NotificationDeliveryStatus status;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "notification_id")
    private Long notificationId;

    @Column(name = "notification_type", length = 50)
    private String notificationType;

    @Column(name = "outbox_event_id", length = 36, updatable = false)
    private String outboxEventId;

    @Column(name = "outbox_event_type", length = 50)
    private String outboxEventType;

    @Column(name = "recipient_hash", length = 64, updatable = false)
    private String recipientHash;

    @Column(name = "provider_message_id", length = 200)
    private String providerMessageId;

    @Column(name = "provider_error_code", length = 100)
    private String providerErrorCode;

    @Column(name = "error_code", length = 80)
    private String errorCode;

    @Column(name = "failure_reason", length = FAILURE_REASON_MAX_LENGTH)
    private String failureReason;

    @Column(name = "retryable", nullable = false)
    private boolean retryable;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static NotificationDelivery create(
            NotificationDeliveryChannel channel,
            Long userId,
            Long notificationId,
            String notificationType,
            String outboxEventId,
            String outboxEventType,
            String recipientHash,
            LocalDateTime now
    ) {
        NotificationDelivery delivery = new NotificationDelivery();
        delivery.channel = channel;
        delivery.userId = userId;
        delivery.notificationId = notificationId;
        delivery.notificationType = notificationType;
        delivery.outboxEventId = outboxEventId;
        delivery.outboxEventType = outboxEventType;
        delivery.recipientHash = recipientHash;
        delivery.createdAt = now;
        delivery.updatedAt = now;
        return delivery;
    }

    public void recordResult(
            NotificationDeliveryStatus status,
            Long userId,
            Long notificationId,
            String notificationType,
            String outboxEventType,
            String providerMessageId,
            String providerErrorCode,
            String errorCode,
            String failureReason,
            boolean retryable,
            int attemptCount,
            LocalDateTime now
    ) {
        this.status = status;
        this.userId = userId;
        this.notificationId = notificationId;
        this.notificationType = notificationType;
        this.outboxEventType = outboxEventType;
        this.providerMessageId = providerMessageId;
        this.providerErrorCode = providerErrorCode;
        this.errorCode = errorCode;
        this.failureReason = truncate(failureReason);
        this.retryable = retryable;
        this.attemptCount = Math.max(attemptCount, 1);
        this.updatedAt = now;
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= FAILURE_REASON_MAX_LENGTH
                ? value
                : value.substring(0, FAILURE_REASON_MAX_LENGTH);
    }
}
