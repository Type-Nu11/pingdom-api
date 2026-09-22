package com.typenull.pingdom.moderation.outbox.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.moderation.application.service.notification.AdminNotificationCreationService;
import com.typenull.pingdom.shared.outbox.application.OutboxEventHandler;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 저장된 알림 payload를 복원해 현재 권한 보유 관리자에게 DB 알림을 생성.
 * 파싱·저장 실패는 작업자에게 전파하며 중복 방지는 outbox eventId가 아닌 payload의 eventKey와 수신자 조합에 따름.
 */
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
