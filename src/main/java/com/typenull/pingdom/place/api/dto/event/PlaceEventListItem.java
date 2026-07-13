package com.typenull.pingdom.place.api.dto.event;

import com.typenull.pingdom.place.domain.event.PlaceEventScheduleStatus;
import com.typenull.pingdom.place.domain.event.PlaceEventType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "공개 기간형 이벤트 목록 항목")
public record PlaceEventListItem(
        Long id,
        Long placeId,
        String placeName,
        String title,
        PlaceEventType eventType,
        LocalDateTime startAt,
        LocalDateTime endAt,
        PlaceEventScheduleStatus scheduleStatus
) {
}
