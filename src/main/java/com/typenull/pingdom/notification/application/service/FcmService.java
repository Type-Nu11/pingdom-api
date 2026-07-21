package com.typenull.pingdom.notification.application.service;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.notification.api.dto.fcm.NotificationResponse;
import com.typenull.pingdom.notification.domain.FcmDeviceToken;
import com.typenull.pingdom.notification.domain.NotificationType;
import com.typenull.pingdom.notification.domain.Notifications;
import com.typenull.pingdom.notification.repository.NotificationsRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        return sendLikeNotification(ownerId, likerId, null);
    }

    @Transactional
    public NotificationResponse sendLikeNotification(Long ownerId, Long likerId, String outboxEventId) {
        if (Objects.equals(ownerId, likerId)) {
            return null;
        }

        User owner = userRepository.findById(ownerId).orElse(null);
        if (owner == null || owner.isWithdrawn()) {
            log.warn("좋아요 알림 수신자를 찾지 못해 전송을 생략합니다. ownerId={}", ownerId);
            return null;
        }

        User liker = userRepository.findById(likerId).orElse(null);
        if (liker == null || liker.isWithdrawn()) {
            log.warn("좋아요 알림 발신자를 찾지 못해 전송을 생략합니다. likerId={}", likerId);
            return null;
        }

        return sendNotification(ownerId, NotificationType.NEW_LIKE, outboxEventId, liker.getUsername());
    }

    @Transactional
    public NotificationResponse sendPlaceInformationReverificationNotification(
            Long userId, NotificationType type, String placeName, String outboxEventId
    ) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || user.isWithdrawn()) {
            log.warn("장소 정보 재확인 알림 수신자를 찾지 못해 전송을 생략합니다. userId={}", userId);
            return null;
        }
        if (type != NotificationType.PLACE_INFORMATION_REVERIFICATION_REQUESTED
                && type != NotificationType.PLACE_INFORMATION_REVERIFICATION_REMINDER) {
            throw new IllegalArgumentException("지원하지 않는 장소 정보 재확인 알림 유형입니다.");
        }
        return sendNotification(userId, type, outboxEventId, placeName);
    }

    private NotificationResponse sendNotification(Long userId, NotificationType type, String outboxEventId, String... args) {
        if (!notificationDeliveryPolicy.canReceive(userId, type)) {
            log.debug("사용자 알림 설정에 의해 발송을 생략합니다. userId={}, type={}", userId, type);
            return null;
        }

        List<FcmDeviceToken> deviceTokens = fcmDeviceTokenService.findTokens(userId);
        if (deviceTokens.isEmpty()) {
            log.debug("수신자의 FCM 토큰이 없어 전송을 생략합니다. userId={}, type={}", userId, type);
            return null;
        }

        String title = type.getTitle();
        String body = type.formatBody(args);
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

        for (FcmDeviceToken deviceToken : deviceTokens) {
            sendToToken(userId, deviceToken.getToken(), type, title, body, savedNotification.getId(), outboxEventId);
        }

        return new NotificationResponse(savedNotification.getId());
    }

    private void sendToToken(
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
                return;
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
        }
    }
}
