package com.typenull.pingdom.notification.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.identity.application.port.EmailSender;
import com.typenull.pingdom.shared.outbox.application.OutboxEventHandler;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PasswordResetOutboxHandler implements OutboxEventHandler {

    private final EmailSender emailSender;
    private final ObjectMapper objectMapper;

    @Override
    public OutboxEventType supportedType() {
        return OutboxEventType.PASSWORD_RESET_REQUESTED;
    }

    @Override
    public void handle(String eventId, String payload) {
        PasswordResetOutboxPayload event = deserialize(payload);
        emailSender.sendPasswordResetEmail(event.recipientEmail(), event.resetToken(), event.expiresAt());
    }

    private PasswordResetOutboxPayload deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, PasswordResetOutboxPayload.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("비밀번호 재설정 Outbox payload 역직렬화에 실패했습니다.", exception);
        }
    }
}
