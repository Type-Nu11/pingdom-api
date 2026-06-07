package com.typenull.pingdom.notification.application.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.exception.AuthErrorCode;
import com.typenull.pingdom.identity.domain.exception.AuthException;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.notification.api.dto.fcm.NotificationResponse;
import com.typenull.pingdom.notification.domain.NotificationType;

import java.time.LocalDateTime;
import java.util.Objects;

import com.typenull.pingdom.notification.domain.Notifications;
import com.typenull.pingdom.notification.domain.exception.NotificationsErrorCode;
import com.typenull.pingdom.notification.domain.exception.NotificationsException;
import com.typenull.pingdom.notification.repository.NotificationsRepository;
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

    @Transactional
    public void updateFcmToken(Long userId, String token) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));
        user.updateFcmToken(token);
    }

    @Transactional
    public NotificationResponse sendNotification(String token, NotificationType type, Long userId, String... args) {
        String title = type.getTitle();
        String body = type.formatBody(args);

        try {
            Notifications savedNotification = notificationsRepository.save(
                    Notifications.builder()
                            .token(token)
                            .userId(userId)
                            .type(type)
                            .title(title)
                            .body(body)
                            .isRead(false)
                            .createdAt(LocalDateTime.now())
                            .build()
            );

            Message message = Message.builder()
                    .setToken(token)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .putData("notificationId", String.valueOf(savedNotification.getId()))
                    .putData("type", type.name())
                    .build();

            String response = FirebaseMessaging.getInstance().send(message);
            log.info("FCM 전송 성공: {}", response);
            return new NotificationResponse(savedNotification.getId());
        } catch (Exception e) {
            log.error("FCM 전송 실패 - type: {}, reason: {}", type, e.getMessage());
            throw new NotificationsException(NotificationsErrorCode.NOTIFICATION_SEND_FAILED);
        }
    }

    @Transactional
    public NotificationResponse sendLikeNotification(Long ownerId, Long likerId) {

        User owner = userRepository.findById(ownerId).orElse(null);
        if (owner == null) {
            log.warn("좋아요 알림 수신자를 찾지 못해 전송을 생략합니다. ownerId={}", ownerId);
            throw new AuthException(AuthErrorCode.USER_NOT_FOUND);
        }

        if (owner.getFcmToken() == null) {
            throw new NotificationsException(NotificationsErrorCode.FCM_TOKEN_NOT_FOUND);
        }

        User liker = userRepository.findById(likerId).orElse(null);
        if (liker == null) {
            log.warn("좋아요 알림 발신자를 찾지 못해 전송을 생략합니다. likerId={}", likerId);
            throw new AuthException(AuthErrorCode.USER_NOT_FOUND);
        }

        return sendNotification(owner.getFcmToken(), NotificationType.NEW_LIKE, ownerId, liker.getUsername());
    }
}
