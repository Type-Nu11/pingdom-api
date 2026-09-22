package com.typenull.pingdom.notification.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.notification.application.service.FcmService;
import com.typenull.pingdom.notification.application.service.FcmDispatchResult;
import com.typenull.pingdom.shared.outbox.application.OutboxEventHandler;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 좋아요 Outbox를 FCM 서비스에 전달하고 남은 일시 실패를 재시도 예외로 전파합니다.
 * FCM 서비스 호출이 반환된 후 예외를 던지므로 서비스의 DB 트랜잭션과 Outbox 재시도 판정을 구분합니다.
 */
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
        FcmDispatchResult result = fcmService.sendLikeNotification(event.ownerId(), event.likerId(), eventId);
        throwIfRetryableFailure(eventId, result);
    }

    private MapImageLikedOutboxPayload deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, MapImageLikedOutboxPayload.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("좋아요 Outbox payload 역직렬화에 실패했습니다.", exception);
        }
    }

    private void throwIfRetryableFailure(String eventId, FcmDispatchResult result) {
        if (result.hasRetryableFailure()) {
            throw new RetryableFcmDeliveryException(eventId);
        }
    }
}
