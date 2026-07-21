package com.typenull.pingdom.notification.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.notification.application.service.FcmService;
import com.typenull.pingdom.notification.domain.NotificationType;
import com.typenull.pingdom.place.outbox.information.PlaceInformationReverificationOutboxPayload;
import com.typenull.pingdom.shared.outbox.application.OutboxEventHandler;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PlaceInformationReverificationRequestedOutboxHandler implements OutboxEventHandler {
    private final FcmService fcmService;
    private final ObjectMapper objectMapper;

    @Override public OutboxEventType supportedType() {
        return OutboxEventType.PLACE_INFORMATION_REVERIFICATION_REQUESTED;
    }

    @Override public void handle(String eventId, String payload) {
        PlaceInformationReverificationOutboxPayload event = deserialize(payload);
        fcmService.sendPlaceInformationReverificationNotification(event.merchantOwnerUserId(),
                NotificationType.PLACE_INFORMATION_REVERIFICATION_REQUESTED, event.placeName(), eventId);
    }

    private PlaceInformationReverificationOutboxPayload deserialize(String payload) {
        try { return objectMapper.readValue(payload, PlaceInformationReverificationOutboxPayload.class); }
        catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("장소 정보 재확인 요청 payload 역직렬화에 실패했습니다.", exception);
        }
    }
}
