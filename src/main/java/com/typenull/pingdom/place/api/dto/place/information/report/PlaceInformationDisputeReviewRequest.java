package com.typenull.pingdom.place.api.dto.place.information.report;

import com.typenull.pingdom.place.domain.place.information.report.PlaceInformationDisputeStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "관리자 장소 정보 반박 검토 요청")
public record PlaceInformationDisputeReviewRequest(
        @NotNull
        @Schema(description = "ACCEPTED 또는 REJECTED만 허용")
        PlaceInformationDisputeStatus status,
        @Size(max = 500)
        String reviewReason
) {
}
