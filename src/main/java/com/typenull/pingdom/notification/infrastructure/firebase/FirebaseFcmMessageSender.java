package com.typenull.pingdom.notification.infrastructure.firebase;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import com.typenull.pingdom.notification.application.service.FcmMessageSender;
import com.typenull.pingdom.notification.application.service.FcmSendException;
import com.typenull.pingdom.notification.domain.NotificationType;
import org.springframework.stereotype.Component;

@Component
public class FirebaseFcmMessageSender implements FcmMessageSender {

    @Override
    public String send(String token, NotificationType type, String title, String body, Long notificationId) {
        Message message = Message.builder()
                .setToken(token)
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .putData("notificationId", String.valueOf(notificationId))
                .putData("type", type.name())
                .build();

        try {
            return FirebaseMessaging.getInstance().send(message);
        } catch (FirebaseMessagingException exception) {
            throw new FcmSendException(
                    "FCM 전송에 실패했습니다.",
                    isInvalidTokenFailure(exception),
                    providerErrorCode(exception),
                    exception
            );
        } catch (RuntimeException exception) {
            throw new FcmSendException("FCM 전송에 실패했습니다.", false, exception);
        }
    }

    private boolean isInvalidTokenFailure(FirebaseMessagingException exception) {
        MessagingErrorCode errorCode = exception.getMessagingErrorCode();
        return errorCode == MessagingErrorCode.UNREGISTERED
                || errorCode == MessagingErrorCode.INVALID_ARGUMENT;
    }

    private String providerErrorCode(FirebaseMessagingException exception) {
        MessagingErrorCode errorCode = exception.getMessagingErrorCode();
        return errorCode == null ? null : errorCode.name();
    }
}
