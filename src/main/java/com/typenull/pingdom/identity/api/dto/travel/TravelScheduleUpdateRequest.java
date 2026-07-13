package com.typenull.pingdom.identity.api.dto.travel;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

@Schema(description = "여행 일정 기간 변경 요청")
public record TravelScheduleUpdateRequest(
        @NotNull(message = "여행 시작일은 필수입니다.")
        @Schema(description = "여행 시작일", example = "2026-08-02")
        LocalDate startDate,
        @NotNull(message = "여행 종료일은 필수입니다.")
        @Schema(description = "여행 종료일", example = "2026-08-05")
        LocalDate endDate
) {
}
