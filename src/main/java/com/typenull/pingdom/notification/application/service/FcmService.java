package com.typenull.pingdom.notification.application.service;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.notification.api.dto.fcm.NotificationResponse;
import com.typenull.pingdom.notification.domain.FcmDeviceToken;
import com.typenull.pingdom.notification.domain.NotificationType;
import com.typenull.pingdom.notification.domain.Notifications;
import com.typenull.pingdom.notification.infrastructure.persistence.NotificationsRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 알림 도메인 이벤트를 FCM 메시지로 변환하고 전송 결과를 기록. */
@Service
@RequiredArgsConstructor
@Slf4j
public class FcmService {

    private final UserRepository userRepository;
    private final NotificationsRepository notificationsRepository;
    private final FcmDeviceTokenService fcmDeviceTokenService;
    private final NotificationDeliveryPolicy notificationDeliveryPolicy;
    private final FcmMessageSender fcmMessageSender;
    private final NotificationDeliveryRecorder notificationDeliveryRecorder;
    private final Clock clock;

    @Transactional
    public NotificationResponse sendLikeNotification(Long ownerId, Long likerId) {
        return sendLikeNotification(ownerId, likerId, null).notification();
    }

    /**
     * 자기 좋아요이거나 발신자·수신자가 없거나 탈퇴했으면 발송을 생략.
     * 그 외에는 좋아요 알림의 설정·토큰 확인 및 전송을 수행하고 앱 알림 ID와 재시도할 토큰 실패 여부를 반환.
     */
    @Transactional
    public FcmDispatchResult sendLikeNotification(Long ownerId, Long likerId, String outboxEventId) {
        if (Objects.equals(ownerId, likerId)) {
            return FcmDispatchResult.skipped();
        }

        User owner = userRepository.findById(ownerId).orElse(null);
        if (owner == null || owner.isWithdrawn()) {
            log.warn("좋아요 알림 수신자를 찾지 못해 전송을 생략합니다. ownerId={}", ownerId);
            return FcmDispatchResult.skipped();
        }

        User liker = userRepository.findById(likerId).orElse(null);
        if (liker == null || liker.isWithdrawn()) {
            log.warn("좋아요 알림 발신자를 찾지 못해 전송을 생략합니다. likerId={}", likerId);
            return FcmDispatchResult.skipped();
        }

        return sendNotification(ownerId, NotificationType.NEW_LIKE, outboxEventId, liker.getUsername());
    }

    /**
     * 수신자가 없거나 탈퇴했으면 생략하고, 장소 정보 재확인 요청·리마인더 이외의 유형은 거부.
     * 유효한 요청은 공통 발송 흐름으로 전달해 알림 ID와 재시도 필요 여부를 반환.
     */
    @Transactional
    public FcmDispatchResult sendPlaceInformationReverificationNotification(
            Long userId, NotificationType type, String placeName, String outboxEventId
    ) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || user.isWithdrawn()) {
            log.warn("장소 정보 재확인 알림 수신자를 찾지 못해 전송을 생략합니다. userId={}", userId);
            return FcmDispatchResult.skipped();
        }
        if (type != NotificationType.PLACE_INFORMATION_REVERIFICATION_REQUESTED
                && type != NotificationType.PLACE_INFORMATION_REVERIFICATION_REMINDER) {
            throw new IllegalArgumentException("지원하지 않는 장소 정보 재확인 알림 유형입니다.");
        }
        return sendNotification(userId, type, outboxEventId, placeName);
    }

    /**
     * 설정·방해금지·등록 토큰을 확인한 뒤 앱 알림을 만들고 토큰별로 전송.
     * Outbox 이벤트의 성공 기록이 있는 토큰은 건너뛰지만, 외부 전송과 DB 기록은 원자적이지 않아 중복 전송 가능성이 남음.
     */
    private FcmDispatchResult sendNotification(Long userId, NotificationType type, String outboxEventId, String... args) {
        if (!notificationDeliveryPolicy.canReceive(userId, type)) {
            log.debug("사용자 알림 설정에 의해 발송을 생략합니다. userId={}, type={}", userId, type);
            return FcmDispatchResult.skipped();
        }

        List<FcmDeviceToken> deviceTokens = fcmDeviceTokenService.findTokens(userId);
        if (deviceTokens.isEmpty()) {
            log.debug("수신자의 FCM 토큰이 없어 전송을 생략합니다. userId={}, type={}", userId, type);
            return FcmDispatchResult.skipped();
        }

        String title = type.getTitle();
        String body = type.formatBody(args);
        Long notificationId = StringUtils.hasText(outboxEventId)
                ? notificationDeliveryRecorder.findFcmNotificationId(outboxEventId)
                : null;
        if (notificationId == null) {
            Notifications savedNotification = notificationsRepository.save(
                    Notifications.builder()
                            .userId(userId)
                            .type(type)
                            .title(title)
                            .body(body)
                            .isRead(false)
                            .createdAt(LocalDateTime.now(clock))
                            .build()
            );
            notificationId = savedNotification.getId();
        }

        boolean hasRetryableFailure = false;
        for (FcmDeviceToken deviceToken : deviceTokens) {
            if (notificationDeliveryRecorder.isFcmDeliverySucceeded(outboxEventId, deviceToken.getToken())) {
                log.info("이미 성공한 FCM 토큰 전송을 재시도에서 생략합니다. type={}, eventId={}", type, outboxEventId);
                continue;
            }
            hasRetryableFailure |= sendToToken(
                    userId,
                    deviceToken.getToken(),
                    type,
                    title,
                    body,
                    notificationId,
                    outboxEventId
            );
        }

        return new FcmDispatchResult(new NotificationResponse(notificationId), hasRetryableFailure);
    }

    /**
     * 무효 토큰은 실패를 기록하고 삭제하여 재시도 대상에서 제외.
     * 그 외 실패는 다른 토큰 처리를 계속하면서 재시도 필요를 반환하며, 결과 기록 실패는 Recorder가 별도로 흡수.
     */
    private boolean sendToToken(
            Long userId,
            String token,
            NotificationType type,
            String title,
            String body,
            Long notificationId,
            String outboxEventId
    ) {
        try {
            String response = fcmMessageSender.send(token, type, title, body, notificationId);
            notificationDeliveryRecorder.recordFcmSuccess(userId, notificationId, type, outboxEventId, token, response);
            log.info("FCM 전송 성공: {}", response);
            return false;
        } catch (FcmSendException exception) {
            if (exception.isInvalidToken()) {
                notificationDeliveryRecorder.recordFcmFailure(
                        userId,
                        notificationId,
                        type,
                        outboxEventId,
                        token,
                        exception.getProviderErrorCode(),
                        NotificationDeliveryRecorder.ERROR_FCM_INVALID_TOKEN,
                        exception.getMessage(),
                        false
                );
                fcmDeviceTokenService.deleteInvalidToken(token);
                log.warn("무효 FCM 토큰을 삭제했습니다. type={}, reason={}", type, exception.getMessage());
                return false;
            }
            notificationDeliveryRecorder.recordFcmFailure(
                    userId,
                    notificationId,
                    type,
                    outboxEventId,
                    token,
                    exception.getProviderErrorCode(),
                    NotificationDeliveryRecorder.ERROR_FCM_SEND_FAILED,
                    exception.getMessage(),
                    true
            );
            log.error(
                    "FCM 개별 토큰 전송 실패 - userId={}, type={}, reason={}",
                    userId,
                    type,
                    exception.getMessage(),
                    exception
            );
            return true;
        } catch (RuntimeException exception) {
            notificationDeliveryRecorder.recordFcmFailure(
                    userId,
                    notificationId,
                    type,
                    outboxEventId,
                    token,
                    null,
                    NotificationDeliveryRecorder.ERROR_FCM_SEND_FAILED,
                    exception.getMessage(),
                    true
            );
            log.error(
                    "FCM 개별 토큰 전송 실패 - userId={}, type={}, reason={}",
                    userId,
                    type,
                    exception.getMessage(),
                    exception
            );
            return true;
        }
    }
}
