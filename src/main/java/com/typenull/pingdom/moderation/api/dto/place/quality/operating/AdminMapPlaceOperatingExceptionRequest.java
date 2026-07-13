package com.typenull.pingdom.moderation.api.dto.place.quality.operating;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.Set;

@Schema(description = "관리자 특정 날짜 영업 예외")
public record AdminMapPlaceOperatingExceptionRequest(
        @NotNull(message = "예외 날짜는 필수입니다.")
        @Schema(example = "2026-08-15")
        LocalDate date,
        @Schema(description = "true이면 종일 휴무이며 hours는 비워야 합니다.", example = "true")
        boolean closed,
        @Schema(description = "대체 영업 시간대. closed가 false이면 하나 이상 필요합니다.")
        Set<@Valid AdminMapPlaceOperatingTimeRangeRequest> hours
) {
}
