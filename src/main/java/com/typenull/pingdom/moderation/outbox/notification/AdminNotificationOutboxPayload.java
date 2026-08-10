package com.typenull.pingdom.moderation.outbox.notification;

import com.typenull.pingdom.notification.domain.NotificationType;
import java.util.List;

public record AdminNotificationOutboxPayload(
        NotificationType type,
        String eventKey,
        String token,
        List<String> bodyArguments
) {
    public AdminNotificationOutboxPayload {
        bodyArguments = bodyArguments == null ? List.of() : List.copyOf(bodyArguments);
    }
}
