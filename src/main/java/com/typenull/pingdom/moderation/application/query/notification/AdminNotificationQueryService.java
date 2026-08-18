package com.typenull.pingdom.moderation.application.query.notification;

import com.typenull.pingdom.moderation.api.dto.notification.AdminNotificationResponse;
import com.typenull.pingdom.moderation.api.dto.notification.AdminNotificationUnreadCountResponse;
import com.typenull.pingdom.notification.domain.NotificationType;
import java.time.LocalDateTime;

public interface AdminNotificationQueryService {

    AdminNotificationResponse listNotifications(
            Long adminUserId,
            NotificationType type,
            Boolean read,
            LocalDateTime from,
            LocalDateTime to,
            int page,
            int limit
    );

    AdminNotificationUnreadCountResponse countUnread(Long adminUserId);
}
