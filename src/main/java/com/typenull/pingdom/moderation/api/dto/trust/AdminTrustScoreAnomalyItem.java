package com.typenull.pingdom.moderation.api.dto.trust;

import com.typenull.pingdom.engagement.domain.policy.TrustScoreAnomaly;
import com.typenull.pingdom.engagement.domain.policy.TrustScoreAnomalySeverity;
import com.typenull.pingdom.engagement.domain.policy.TrustScoreAnomalyType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record AdminTrustScoreAnomalyItem(
        @Schema(description = "Trust Score 이상치 ID", example = "1")
        Long id,
        @Schema(description = "신고자 사용자 ID", example = "12")
        Long reporterUserId,
        @Schema(description = "신고자 username", example = "ping-user")
        String reporterUsername,
        @Schema(description = "이상치 유형", example = "FALSE_REPORT_SPIKE")
        TrustScoreAnomalyType anomalyType,
        @Schema(description = "이상치 심각도", example = "HIGH")
        TrustScoreAnomalySeverity severity,
        @Schema(description = "기준 Trust Score", example = "100")
        int baselineScore,
        @Schema(description = "감지 시점 Trust Score", example = "40")
        int observedScore,
        @Schema(description = "전체 신고 수", example = "5")
        long submittedCount,
        @Schema(description = "수락 신고 수", example = "1")
        long acceptedCount,
        @Schema(description = "거절 신고 수", example = "4")
        long declinedCount,
        @Schema(description = "허위 신고 수", example = "4")
        long falseReportCount,
        @Schema(description = "감지 시각", example = "2026-07-20T18:00:00")
        LocalDateTime detectedAt,
        @Schema(description = "해결 시각", example = "2026-07-20T19:00:00")
        LocalDateTime resolvedAt,
        @Schema(description = "해결 사유", example = "관리자 검토 완료")
        String resolutionReason
) {

    public static AdminTrustScoreAnomalyItem from(TrustScoreAnomaly anomaly) {
        return new AdminTrustScoreAnomalyItem(
                anomaly.getId(),
                anomaly.getReporterUserId(),
                anomaly.getReporterUsername(),
                anomaly.getAnomalyType(),
                anomaly.getSeverity(),
                anomaly.getBaselineScore(),
                anomaly.getObservedScore(),
                anomaly.getSubmittedCount(),
                anomaly.getAcceptedCount(),
                anomaly.getDeclinedCount(),
                anomaly.getFalseReportCount(),
                anomaly.getDetectedAt(),
                anomaly.getResolvedAt(),
                anomaly.getResolutionReason()
        );
    }
}
