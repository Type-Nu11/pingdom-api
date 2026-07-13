package com.typenull.pingdom.identity.api.dto.travel;

import com.typenull.pingdom.identity.domain.travel.TravelSchedule;
import com.typenull.pingdom.identity.domain.travel.TravelScheduleStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

@Schema(description = "여행 일정 응답")
public record TravelScheduleResponse(
        @Schema(description = "여행 일정 ID", example = "1")
        Long id,
        @Schema(description = "여행 시작일", example = "2026-08-01")
        LocalDate startDate,
        @Schema(description = "여행 종료일", example = "2026-08-04")
        LocalDate endDate,
        @Schema(description = "기준일에 계산한 일정 상태", example = "UPCOMING")
        TravelScheduleStatus status
) {

    public static TravelScheduleResponse from(TravelSchedule schedule, LocalDate referenceDate) {
        return new TravelScheduleResponse(
                schedule.getId(),
                schedule.getStartDate(),
                schedule.getEndDate(),
                schedule.statusAt(referenceDate)
        );
    }
}
