package com.typenull.pingdom.identity.api.dto.travel;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "여행 일정 목록 응답")
public record TravelScheduleListResponse(
        @Schema(description = "여행 일정 목록")
        List<TravelScheduleResponse> schedules
) {
}
