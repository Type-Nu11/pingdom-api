package com.typenull.pingdom.place.api.dto.place.information.report;

import com.typenull.pingdom.place.domain.place.information.report.PlaceInformationReportReasonType;
import com.typenull.pingdom.place.domain.place.information.report.PlaceInformationReportTargetType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 장소 정보 신고 대상·사유와 선택적인 검증 근거 ID를 전달.
 * 근거가 있다면 같은 장소에 속해야 하고 설명의 공백 여부는 도메인 생성 시 추가 확인.
 */
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
