package com.typenull.pingdom.moderation.api.dto.place.quality;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;

@Schema(description = "관리자 장소 운영 시간대")
public record AdminMapPlaceOperatingTimeRangeRequest(
        @NotNull(message = "영업 시작 시각은 필수입니다.")
        @Schema(type = "string", format = "time", example = "09:00:00")
        LocalTime opensAt,
        @NotNull(message = "영업 종료 시각은 필수입니다.")
        @Schema(type = "string", format = "time", example = "18:00:00")
        LocalTime closesAt
) {
}
