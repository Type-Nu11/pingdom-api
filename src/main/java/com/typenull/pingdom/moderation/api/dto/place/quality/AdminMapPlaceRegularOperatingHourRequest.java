package com.typenull.pingdom.moderation.api.dto.place.quality;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;
import java.time.LocalTime;

@Schema(description = "관리자 요일별 정규 영업 시간대")
public record AdminMapPlaceRegularOperatingHourRequest(
        @NotNull(message = "요일은 필수입니다.")
        @Schema(example = "MONDAY")
        DayOfWeek dayOfWeek,
        @NotNull(message = "영업 시작 시각은 필수입니다.")
        @Schema(example = "09:00:00")
        LocalTime opensAt,
        @NotNull(message = "영업 종료 시각은 필수입니다.")
        @Schema(example = "18:00:00")
        LocalTime closesAt
) {
}
