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
public class EmailVerificationOutboxHandler implements OutboxEventHandler {

    private final EmailSender emailSender;
    private final ObjectMapper objectMapper;

    @Override
    public OutboxEventType supportedType() {
        return OutboxEventType.EMAIL_VERIFICATION_REQUESTED;
    }

    @Override
    public void handle(String eventId, String payload) {
        EmailVerificationOutboxPayload event = deserialize(payload);
        emailSender.sendVerificationEmail(event.recipientEmail(), event.verificationCode());
    }

    private EmailVerificationOutboxPayload deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, EmailVerificationOutboxPayload.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("이메일 Outbox payload 역직렬화에 실패했습니다.", exception);
        }
    }
}
