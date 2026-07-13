package com.typenull.pingdom.moderation.api.dto.place.event;

import com.typenull.pingdom.place.domain.event.PlaceEventPublicationStatus;
import com.typenull.pingdom.place.domain.event.PlaceEventType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "관리자 기간형 이벤트 응답")
public record AdminPlaceEventResponse(
        Long eventId,
        Long placeId,
        String placeName,
        String title,
        String description,
        PlaceEventType eventType,
        LocalDateTime startAt,
        LocalDateTime endAt,
        PlaceEventPublicationStatus publicationStatus,
        String message
) {
}
