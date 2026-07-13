package com.typenull.pingdom.place.api.dto.place;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.DayOfWeek;
import java.time.LocalTime;

@Schema(description = "요일별 정규 영업 시간대")
public record PlaceRegularOperatingHourResponse(
        @Schema(example = "MONDAY")
        DayOfWeek dayOfWeek,
        @Schema(type = "string", format = "time", example = "09:00:00")
        LocalTime opensAt,
        @Schema(type = "string", format = "time", example = "18:00:00")
        LocalTime closesAt
) {
}
