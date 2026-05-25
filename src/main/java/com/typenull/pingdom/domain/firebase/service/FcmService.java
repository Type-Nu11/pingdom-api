package com.typenull.pingdom.domain.firebase.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.typenull.pingdom.domain.auth.domain.User;
import com.typenull.pingdom.domain.auth.exception.AuthErrorCode;
import com.typenull.pingdom.domain.auth.exception.AuthException;
import com.typenull.pingdom.domain.auth.repository.UserRepository;
import com.typenull.pingdom.domain.firebase.enums.NotificationType;
import com.typenull.pingdom.domain.map.domain.MapImage;
import com.typenull.pingdom.domain.map.exception.MapErrorCode;
import com.typenull.pingdom.domain.map.exception.MapException;
import com.typenull.pingdom.domain.map.repository.MapImageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FcmService {

    private final UserRepository userRepository;
    private final MapImageRepository mapImageRepository;

    @Transactional
    public void updateFcmToken(Long userId, String token) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));
        user.updateFcmToken(token);
    }

    public void sendNotification(String token, NotificationType type, String... args) throws FirebaseMessagingException {
        Message message = Message.builder()
                .setToken(token)
                .setNotification(Notification.builder()
                        .setTitle(type.getTitle())
                        .setBody(type.formatBody(args))
                        .build())
                .putData("type", type.name())
                .build();

        FirebaseMessaging.getInstance().send(message);
    }

    public void likePlace(Long imageId, Long userId){
        MapImage mapImage = mapImageRepository.findById(imageId)
                .orElseThrow(() -> new MapException(MapErrorCode.IMAGE_NOT_FOUND));

        User owner = userRepository.findById(mapImage.getUserId())
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));

        User liker = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(AuthErrorCode.USER_NOT_FOUND));

        if (owner.getFcmToken() != null) {
            try {
                sendNotification(
                        owner.getFcmToken(),
                        NotificationType.NEW_LIKE,
                        liker.getUsername()
                );
            } catch (FirebaseMessagingException e) {
                log.error("FCM 전송 실패: {}", e.getMessage());
            }
        }
    }
}
