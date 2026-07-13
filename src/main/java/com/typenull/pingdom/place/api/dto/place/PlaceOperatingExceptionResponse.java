package com.typenull.pingdom.place.api.dto.place;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "특정 날짜의 휴무 또는 대체 영업 시간")
public record PlaceOperatingExceptionResponse(
        @Schema(example = "2026-08-15")
        LocalDate date,
        @Schema(description = "true이면 종일 휴무이며 hours는 비어 있습니다.")
        boolean closed,
        List<PlaceOperatingTimeRangeResponse> hours
) {
}
