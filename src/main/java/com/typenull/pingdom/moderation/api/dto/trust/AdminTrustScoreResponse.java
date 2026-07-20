package com.typenull.pingdom.moderation.api.dto.trust;

import com.typenull.pingdom.engagement.domain.policy.TrustScoreGrade;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "신고자 신뢰 등급 및 점수 근거 응답")
public record AdminTrustScoreResponse(
        @Schema(description = "신고자 사용자 ID", example = "7")
        Long reporterUserId,

        @Schema(description = "신고자 username", example = "pingdom_user")
        String reporterUsername,

        @Schema(description = "신뢰 점수", example = "80")
        int trustScore,

        @Schema(description = "신뢰 등급", example = "HIGH")
        TrustScoreGrade trustGrade,

        @Schema(description = "현재 신고 제한 여부", example = "false")
        boolean restricted,

        @Schema(description = "신고 제한 종료 시각", nullable = true, example = "2026-07-27T12:00:00")
        LocalDateTime restrictedUntil,

        @Schema(description = "신고 제한 사유", nullable = true, example = "FALSE_REPORT_THRESHOLD_EXCEEDED")
        String restrictionReason,

        @Schema(description = "신뢰 점수 산정 근거")
        AdminTrustScoreEvidenceResponse evidence
) {
}
