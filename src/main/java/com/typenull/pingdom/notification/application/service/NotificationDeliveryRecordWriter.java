package com.typenull.pingdom.notification.application.service;

import com.typenull.pingdom.notification.domain.NotificationDelivery;
import com.typenull.pingdom.notification.domain.NotificationDeliveryStatus;
import com.typenull.pingdom.notification.infrastructure.persistence.NotificationDeliveryRepository;
import com.typenull.pingdom.shared.outbox.application.OutboxProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
class NotificationDeliveryRecordWriter {

    private final NotificationDeliveryRepository notificationDeliveryRepository;
    private final OutboxProperties outboxProperties;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(NotificationDeliveryRecordRequest request) {
        LocalDateTime now = LocalDateTime.now(clock);
        String recipientHash = hashRecipient(request);
        NotificationDelivery delivery = findExisting(request, recipientHash)
                .orElseGet(() -> NotificationDelivery.create(
                        request.channel(),
                        request.userId(),
                        request.notificationId(),
                        request.notificationType(),
                        request.outboxEventId(),
                        request.outboxEventType(),
                        recipientHash,
                        now
                ));

        int nextAttemptCount = delivery.getAttemptCount() + 1;
        NotificationDeliveryStatus status = resolveStatus(request.status(), nextAttemptCount);
        delivery.recordResult(
                status,
                request.userId(),
                request.notificationId(),
                request.notificationType(),
                request.outboxEventType(),
                request.providerMessageId(),
                request.providerErrorCode(),
                request.errorCode(),
                request.failureReason(),
                request.retryable(),
                nextAttemptCount,
                now
        );
        notificationDeliveryRepository.save(delivery);
    }

    private java.util.Optional<NotificationDelivery> findExisting(
            NotificationDeliveryRecordRequest request,
            String recipientHash
    ) {
        if (!StringUtils.hasText(request.outboxEventId())) {
            return java.util.Optional.empty();
        }
        return notificationDeliveryRepository.findDeliveryRecord(
                request.outboxEventId(),
                request.channel(),
                recipientHash
        );
    }

    private NotificationDeliveryStatus resolveStatus(NotificationDeliveryStatus requestedStatus, int nextAttemptCount) {
        if (requestedStatus == NotificationDeliveryStatus.RETRY_SCHEDULED
                && nextAttemptCount >= Math.max(outboxProperties.maxAttempts(), 1)) {
            return NotificationDeliveryStatus.FINAL_FAILED;
        }
        return requestedStatus;
    }

    private String hashRecipient(NotificationDeliveryRecordRequest request) {
        if (!StringUtils.hasText(request.recipient())) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    (request.channel().name() + ":" + request.recipient().trim())
                            .getBytes(StandardCharsets.UTF_8)
            );
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest를 사용할 수 없습니다.", exception);
        }
    }
}
