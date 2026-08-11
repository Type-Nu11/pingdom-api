package com.typenull.pingdom.place.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.place.application.service.recommendation.snapshot.PlaceRecommendationSnapshotResyncService;
import com.typenull.pingdom.shared.outbox.application.OutboxEventHandler;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PlaceRecommendationResyncOutboxHandler implements OutboxEventHandler {

    private final PlaceRecommendationSnapshotResyncService resyncService;
    private final ObjectMapper objectMapper;

    @Override
    public OutboxEventType supportedType() {
        return OutboxEventType.PLACE_RECOMMENDATION_RESYNC_REQUESTED;
    }

    @Override
    public void handle(String eventId, String payload) {
        PlaceRecommendationResyncOutboxPayload event = deserialize(payload);
        if (event.placeId() == null) {
            throw new IllegalArgumentException("장소 추천 재동기화 Outbox payload에 placeId가 없습니다.");
        }
        resyncService.resyncPlace(event.placeId());
        log.info(
                "장소 보정 후 추천 snapshot 재동기화를 완료했습니다. eventId={}, placeId={}, reason={}",
                eventId,
                event.placeId(),
                event.reason()
        );
    }

    private PlaceRecommendationResyncOutboxPayload deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, PlaceRecommendationResyncOutboxPayload.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("장소 추천 재동기화 Outbox payload 역직렬화에 실패했습니다.", exception);
        }
    }
}
