package com.typenull.pingdom.notification.application.service;

import com.typenull.pingdom.notification.domain.NotificationType;

public interface FcmMessageSender {

    String send(String token, NotificationType type, String title, String body, Long notificationId);
}
