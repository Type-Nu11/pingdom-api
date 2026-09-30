package com.typenull.pingdom.place.api.dto.place.operating;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalTime;

@Schema(description = "Asia/Seoul 기준 하루 중 장소 운영 시간대")
public record PlaceOperatingTimeRangeResponse(
        @Schema(description = "Asia/Seoul 기준 시작 시각(포함)", type = "string", format = "time", example = "09:00:00")
        LocalTime opensAt,
        @Schema(description = "Asia/Seoul 기준 종료 시각(제외)", type = "string", format = "time", example = "18:00:00")
        LocalTime closesAt
) {
}
