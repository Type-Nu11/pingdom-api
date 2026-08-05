package com.typenull.pingdom.place.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.shared.outbox.application.OutboxEventHandler;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PlaceConversionEventOutboxHandler implements OutboxEventHandler {

    private final ObjectMapper objectMapper;

    @Override
    public OutboxEventType supportedType() {
        return OutboxEventType.PLACE_CONVERSION_RECORDED;
    }

    @Override
    public void handle(String eventId, String payload) {
        PlaceConversionEventOutboxPayload event = deserialize(payload);
        log.info(
                "장소 전환 이벤트를 처리했습니다. eventId={}, conversionEventId={}, type={}, sourceId={}",
                eventId,
                event.conversionEventId(),
                event.conversionType(),
                event.sourceId()
        );
    }

    private PlaceConversionEventOutboxPayload deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, PlaceConversionEventOutboxPayload.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("장소 전환 이벤트 Outbox payload 역직렬화에 실패했습니다.", exception);
        }
    }
}
