package com.typenull.pingdom.moderation.outbox.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.moderation.application.service.notification.AdminNotificationCreationService;
import com.typenull.pingdom.shared.outbox.application.OutboxEventHandler;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminNotificationOutboxHandler implements OutboxEventHandler {

    private final AdminNotificationCreationService creationService;
    private final ObjectMapper objectMapper;

    @Override
    public OutboxEventType supportedType() {
        return OutboxEventType.ADMIN_NOTIFICATION_REQUESTED;
    }

    @Override
    public void handle(String eventId, String payload) {
        AdminNotificationOutboxPayload event = deserialize(payload);
        creationService.create(
                event.type(),
                event.eventKey(),
                event.token(),
                event.bodyArguments()
        );
    }

    private AdminNotificationOutboxPayload deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, AdminNotificationOutboxPayload.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("관리자 알림 Outbox payload 역직렬화에 실패했습니다.", exception);
        }
    }
}
