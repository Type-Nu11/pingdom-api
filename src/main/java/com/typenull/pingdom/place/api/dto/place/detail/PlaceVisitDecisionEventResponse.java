package com.typenull.pingdom.place.api.dto.place.detail;

import com.typenull.pingdom.place.domain.event.PlaceEvent;
import com.typenull.pingdom.place.domain.event.PlaceEventScheduleStatus;
import com.typenull.pingdom.place.domain.event.PlaceEventType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "방문 결정 화면에 노출하는 진행 중 장소 이벤트")
public record PlaceVisitDecisionEventResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Long eventId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String title,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) String description,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        PlaceEventType eventType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDateTime startAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDateTime endAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        PlaceEventScheduleStatus scheduleStatus
) {

    public static PlaceVisitDecisionEventResponse from(PlaceEvent event, LocalDateTime checkedAt) {
        return new PlaceVisitDecisionEventResponse(
                event.getId(),
                event.getTitle(),
                event.getDescription(),
                event.getEventType(),
                event.getStartAt(),
                event.getEndAt(),
                event.scheduleStatusAt(checkedAt)
        );
    }
}
