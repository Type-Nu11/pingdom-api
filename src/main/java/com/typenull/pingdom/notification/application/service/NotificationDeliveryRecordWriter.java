package com.typenull.pingdom.notification.application.service;

import com.typenull.pingdom.notification.domain.NotificationDelivery;
import com.typenull.pingdom.notification.domain.NotificationDeliveryChannel;
import com.typenull.pingdom.notification.domain.NotificationDeliveryStatus;
import com.typenull.pingdom.notification.infrastructure.persistence.NotificationDeliveryRepository;
import com.typenull.pingdom.shared.outbox.application.OutboxProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 발송 결과를 REQUIRES_NEW 트랜잭션으로 남겨 호출 작업의 롤백과 분리.
 * Outbox·채널·수신자 해시별 기록을 갱신하며 원본 이메일/토큰 대신 채널을 포함한 SHA-256을 저장.
 */
@Service
@RequiredArgsConstructor
class NotificationDeliveryRecordWriter {

    private final NotificationDeliveryRepository notificationDeliveryRepository;
    private final OutboxProperties outboxProperties;
    private final Clock clock;

    /**
     * Outbox ID가 있는 결과는 채널·수신자 해시로 기존 기록을 찾아 시도 수를 증가시키고, 없으면 새 기록을 생성.
     * 최대 재시도 도달 여부를 결과 상태에 반영하여 독립 트랜잭션으로 저장. 외부 발송과 실제 재시도 예약은 처리 범위 외.
     */
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

    /** Outbox ID와 토큰으로 찾은 FCM 발송 기록이 SUCCEEDED인지 확인. 입력이 비어 있거나 기록이 없으면 false를 반환해 발송 생략 근거에서 제외. */
    @Transactional(readOnly = true)
    public boolean isFcmDeliverySucceeded(String outboxEventId, String token) {
        if (!StringUtils.hasText(outboxEventId) || !StringUtils.hasText(token)) {
            return false;
        }

        return notificationDeliveryRepository.findDeliveryRecord(
                        outboxEventId,
                        NotificationDeliveryChannel.FCM,
                        hashRecipient(NotificationDeliveryChannel.FCM, token)
                )
                .map(delivery -> delivery.getStatus() == NotificationDeliveryStatus.SUCCEEDED)
                .orElse(false);
    }

    /**
     * 같은 Outbox 이벤트의 FCM 기록 중 알림 ID가 있는 가장 이른 기록을 찾아 재전송에 재사용.
     * 발송 성공 상태로 제한하지 않으며 이벤트 ID나 해당 기록이 없으면 null을 반환.
     */
    @Transactional(readOnly = true)
    public Long findFcmNotificationId(String outboxEventId) {
        if (!StringUtils.hasText(outboxEventId)) {
            return null;
        }

        return notificationDeliveryRepository
                .findFirstByOutboxEventIdAndChannelAndNotificationIdIsNotNullOrderByCreatedAtAsc(
                        outboxEventId,
                        NotificationDeliveryChannel.FCM
                )
                .map(NotificationDelivery::getNotificationId)
                .orElse(null);
    }

    private Optional<NotificationDelivery> findExisting(
            NotificationDeliveryRecordRequest request,
            String recipientHash
    ) {
        if (!StringUtils.hasText(request.outboxEventId())) {
            return Optional.empty();
        }
        return notificationDeliveryRepository.findDeliveryRecord(
                request.outboxEventId(),
                request.channel(),
                recipientHash
        );
    }

    /**
     * 요청 상태가 RETRY_SCHEDULED인 경우에만 누적 시도 수와 Outbox 최대 횟수를 비교해 FINAL_FAILED로 표현.
     * 실제 재시도 예약과 원본 Outbox 상태 변경은 처리 범위 외.
     */
    private NotificationDeliveryStatus resolveStatus(NotificationDeliveryStatus requestedStatus, int nextAttemptCount) {
        if (requestedStatus == NotificationDeliveryStatus.RETRY_SCHEDULED
                && nextAttemptCount >= Math.max(outboxProperties.maxAttempts(), 1)) {
            return NotificationDeliveryStatus.FINAL_FAILED;
        }
        return requestedStatus;
    }

    private String hashRecipient(NotificationDeliveryRecordRequest request) {
        return hashRecipient(request.channel(), request.recipient());
    }

    private String hashRecipient(NotificationDeliveryChannel channel, String recipient) {
        if (!StringUtils.hasText(recipient)) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    (channel.name() + ":" + recipient.trim())
                            .getBytes(StandardCharsets.UTF_8)
            );
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest를 사용할 수 없습니다.", exception);
        }
    }
}
