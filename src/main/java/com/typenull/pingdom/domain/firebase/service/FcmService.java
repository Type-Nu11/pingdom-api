package com.typenull.pingdom.domain.firebase.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.typenull.pingdom.domain.auth.domain.User;
import com.typenull.pingdom.domain.auth.exception.AuthErrorCode;
import com.typenull.pingdom.domain.auth.exception.AuthException;
import com.typenull.pingdom.domain.auth.repository.UserRepository;
import com.typenull.pingdom.domain.firebase.enums.NotificationType;
import com.typenull.pingdom.domain.map.domain.MapImage;
import com.typenull.pingdom.domain.map.repository.MapImageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
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

    @Async
    public void sendLikeNotification(MapImage mapImage, Long likerId) {

        Long ownerId = mapImage.getUserId();

        // 본인 좋아요는 알림 생략
        if (ownerId.equals(likerId)) return;

        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));

        if (owner.getFcmToken() == null) return;

        User liker = userRepository.findById(likerId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));

        sendNotification(owner.getFcmToken(), NotificationType.NEW_LIKE, liker.getUsername());
    }
}
