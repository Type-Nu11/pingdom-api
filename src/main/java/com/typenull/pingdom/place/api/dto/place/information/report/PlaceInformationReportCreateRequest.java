package com.typenull.pingdom.place.api.dto.place.information.report;

import com.typenull.pingdom.place.domain.place.information.report.PlaceInformationReportReasonType;
import com.typenull.pingdom.place.domain.place.information.report.PlaceInformationReportTargetType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "장소 정보 신고 생성 요청")
public record PlaceInformationReportCreateRequest(
        @Schema(nullable = true, description = "신고 대상 증빙 ID")
        Long evidenceId,
        @NotNull
        PlaceInformationReportTargetType targetType,
        @NotNull
        PlaceInformationReportReasonType reasonType,
        @Size(min = 1, max = 1000)
        String description,
        @Schema(nullable = true)
        @Size(max = 500)
        String evidenceUrl
) {
}
