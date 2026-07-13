package com.typenull.pingdom.moderation.api.dto.place.quality.operating;

import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record AdminMapPlaceOperatingStatusUpdateResponse(
        Long placeId,
        PlaceOperatingStatus operatingStatus,
        @Schema(description = "운영 상태 최신 확인 시각")
        LocalDateTime operatingStatusCheckedAt,
        String message
) {
}
