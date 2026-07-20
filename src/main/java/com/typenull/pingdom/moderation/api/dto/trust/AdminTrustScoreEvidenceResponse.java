package com.typenull.pingdom.moderation.api.dto.trust;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "신뢰 점수 산정 근거")
public record AdminTrustScoreEvidenceResponse(
        @Schema(description = "신고 제출 수", example = "12")
        long submittedCount,

        @Schema(description = "승인된 신고 수", example = "8")
        long acceptedCount,

        @Schema(description = "반려된 신고 수", example = "4")
        long declinedCount,

        @Schema(description = "허위 신고 수", example = "3")
        long falseReportCount,

        @Schema(description = "승인률", example = "66.67")
        double acceptanceRate,

        @Schema(description = "기본 점수", example = "100")
        int baseScore,

        @Schema(description = "승인 신고 가산점 합계", example = "40")
        long acceptedScoreBonus,

        @Schema(description = "허위 신고 감점 합계", example = "60")
        long falseReportScorePenalty
) {
}
