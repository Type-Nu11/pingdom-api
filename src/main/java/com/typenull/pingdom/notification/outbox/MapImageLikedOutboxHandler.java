package com.typenull.pingdom.notification.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.notification.application.service.FcmService;
import com.typenull.pingdom.shared.outbox.application.OutboxEventHandler;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MapImageLikedOutboxHandler implements OutboxEventHandler {

    private final FcmService fcmService;
    private final ObjectMapper objectMapper;

    @Override
    public OutboxEventType supportedType() {
        return OutboxEventType.MAP_IMAGE_LIKED;
    }

    @Override
    public void handle(String eventId, String payload) {
        MapImageLikedOutboxPayload event = deserialize(payload);
        fcmService.sendLikeNotification(event.ownerId(), event.likerId());
    }

    private MapImageLikedOutboxPayload deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, MapImageLikedOutboxPayload.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("좋아요 Outbox payload 역직렬화에 실패했습니다.", exception);
        }
    }
}
