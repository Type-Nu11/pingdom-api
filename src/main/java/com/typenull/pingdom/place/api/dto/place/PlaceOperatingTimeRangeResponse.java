package com.typenull.pingdom.place.api.dto.place;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalTime;

@Schema(description = "하루 중 장소 운영 시간대")
public record PlaceOperatingTimeRangeResponse(
        @Schema(type = "string", format = "time", example = "09:00:00")
        LocalTime opensAt,
        @Schema(type = "string", format = "time", example = "18:00:00")
        LocalTime closesAt
) {
}
