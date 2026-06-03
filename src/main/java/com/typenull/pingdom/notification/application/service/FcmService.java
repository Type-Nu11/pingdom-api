package com.typenull.pingdom.notification.application.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.exception.AuthErrorCode;
import com.typenull.pingdom.identity.domain.exception.AuthException;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import com.typenull.pingdom.notification.domain.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FcmService {

    private final UserRepository userRepository;

    @Transactional
    public void updateFcmToken(Long userId, String token) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));
        user.updateFcmToken(token);
    }

    public void sendNotification(String token, NotificationType type, String... args) {
        try {
            Message message = Message.builder()
                    .setToken(token)
                    .setNotification(Notification.builder()
                            .setTitle(type.getTitle())
                            .setBody(type.formatBody(args))
                            .build())
                    .putData("type", type.name())
                    .build();

            String response = FirebaseMessaging.getInstance().send(message);
            log.info("FCM 전송 성공: {}", response);
        } catch (Exception e) {
            log.error("FCM 전송 실패 - type: {}, reason: {}", type, e.getMessage());
        }
    }

    public void sendLikeNotification(Long ownerId, Long likerId) {
        // 본인 좋아요는 알림 생략
        if (ownerId.equals(likerId)) {
            return;
        }

        User owner = userRepository.findById(ownerId).orElse(null);
        if (owner == null) {
            log.warn("좋아요 알림 수신자를 찾지 못해 전송을 생략합니다. ownerId={}", ownerId);
            return;
        }

        if (owner.getFcmToken() == null) {
            return;
        }

        User liker = userRepository.findById(likerId).orElse(null);
        if (liker == null) {
            log.warn("좋아요 알림 발신자를 찾지 못해 전송을 생략합니다. likerId={}", likerId);
            return;
        }

        sendNotification(owner.getFcmToken(), NotificationType.NEW_LIKE, liker.getUsername());
    }
}
