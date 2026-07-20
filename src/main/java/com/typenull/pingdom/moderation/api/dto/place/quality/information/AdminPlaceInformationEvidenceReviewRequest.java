package com.typenull.pingdom.moderation.api.dto.place.quality.information;

import com.typenull.pingdom.place.domain.place.information.PlaceInformationVerificationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "관리자 장소 정보 증빙 검토 요청")
public record AdminPlaceInformationEvidenceReviewRequest(
        @NotNull
        @Schema(description = "관리자 검토 결과. ADMIN_VERIFIED 또는 REJECTED만 허용")
        PlaceInformationVerificationStatus verificationStatus,
        @Schema(nullable = true, description = "검토 사유. REJECTED일 때 필수")
        @Size(max = 500)
        String reviewReason
) {
}
