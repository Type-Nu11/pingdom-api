package com.typenull.pingdom.place.api.dto.event;

import com.typenull.pingdom.place.domain.event.PlaceEventType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(description = "공개 기간형 이벤트 상세 응답")
public record PlaceEventDetailResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "1") Long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "101") Long placeId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "진주성") String placeName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "경상남도 진주시 남강로 626") String placeAddress,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "진주 여름 빛 축제") String title,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, example = "남강 야간 전시와 공연") String description,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "EXHIBITION") PlaceEventType eventType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "date-time", example = "2026-08-01T00:00:00Z")
        OffsetDateTime startAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "date-time", example = "2026-08-08T00:00:00Z")
        OffsetDateTime endAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"UPCOMING", "ONGOING"}, example = "UPCOMING")
        String scheduleStatus
) {
}
