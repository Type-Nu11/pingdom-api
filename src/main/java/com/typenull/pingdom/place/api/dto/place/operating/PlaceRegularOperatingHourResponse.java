package com.typenull.pingdom.place.api.dto.place.operating;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.DayOfWeek;
import java.time.LocalTime;

@Schema(description = "Asia/Seoul 기준 요일별 정규 영업 시간대")
public record PlaceRegularOperatingHourResponse(
        @Schema(description = "Asia/Seoul 기준 요일", example = "MONDAY")
        DayOfWeek dayOfWeek,
        @Schema(description = "Asia/Seoul 기준 시작 시각(포함)", type = "string", format = "time", example = "09:00:00")
        LocalTime opensAt,
        @Schema(description = "Asia/Seoul 기준 종료 시각(제외)", type = "string", format = "time", example = "18:00:00")
        LocalTime closesAt
) {
}
