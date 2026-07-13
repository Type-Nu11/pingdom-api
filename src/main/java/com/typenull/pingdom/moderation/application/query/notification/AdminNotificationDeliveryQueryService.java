package com.typenull.pingdom.moderation.application.query.notification;

import com.typenull.pingdom.moderation.api.dto.notification.AdminNotificationDeliveryResponse;
import com.typenull.pingdom.notification.domain.NotificationDeliveryChannel;
import com.typenull.pingdom.notification.domain.NotificationDeliveryStatus;
import com.typenull.pingdom.notification.domain.NotificationType;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import java.time.LocalDateTime;

public interface AdminNotificationDeliveryQueryService {

    AdminNotificationDeliveryResponse listDeliveries(
            Long userId,
            NotificationDeliveryChannel channel,
            NotificationDeliveryStatus status,
            NotificationType notificationType,
            OutboxEventType outboxEventType,
            LocalDateTime from,
            LocalDateTime to,
            int page,
            int limit
    );
}
