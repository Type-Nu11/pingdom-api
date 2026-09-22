package com.typenull.pingdom.place.api.dto.place.information.report;

import com.typenull.pingdom.place.domain.place.information.report.PlaceInformationReportStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 신고 심사의 다음 상태와 사유를 전달.
 * 서비스는 UNDER_REVIEW·ACCEPTED·REJECTED·RESOLVED만 받고 각 현재 상태의 전이 가능 여부는 도메인이 검사.
 */
@Schema(description = "관리자 장소 정보 신고 검토 요청")
public record PlaceInformationReportReviewRequest(
        @NotNull
        @Schema(description = "UNDER_REVIEW, ACCEPTED, REJECTED, RESOLVED만 허용")
        PlaceInformationReportStatus status,
        @Size(max = 500)
        String reviewReason
) {
}
