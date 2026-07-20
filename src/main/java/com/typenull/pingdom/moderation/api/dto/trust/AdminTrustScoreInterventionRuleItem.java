package com.typenull.pingdom.moderation.api.dto.trust;

import com.typenull.pingdom.engagement.domain.policy.TrustScoreInterventionAction;
import com.typenull.pingdom.engagement.domain.policy.TrustScoreInterventionRule;
import com.typenull.pingdom.engagement.domain.policy.TrustScoreInterventionTrigger;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record AdminTrustScoreInterventionRuleItem(
        @Schema(description = "개입 규칙 ID", example = "1")
        Long id,
        @Schema(description = "규칙 이름", example = "low trust review")
        String ruleName,
        @Schema(description = "트리거 유형", example = "FALSE_REPORT_COUNT")
        TrustScoreInterventionTrigger triggerType,
        @Schema(description = "액션 유형", example = "TEMPORARY_RESTRICT")
        TrustScoreInterventionAction actionType,
        @Schema(description = "활성화 여부", example = "true")
        boolean enabled,
        @Schema(description = "최소 Trust Score", example = "0")
        int minTrustScore,
        @Schema(description = "최대 Trust Score", example = "60")
        int maxTrustScore,
        @Schema(description = "최소 신고 수", example = "3")
        long minSubmittedCount,
        @Schema(description = "최소 허위 신고 수", example = "3")
        long minFalseReportCount,
        @Schema(description = "제한 기간 일수", example = "7")
        Integer durationDays,
        @Schema(description = "우선순위", example = "10")
        int priority,
        @Schema(description = "적용 사유", example = "허위 신고 누적")
        String reason,
        @Schema(description = "생성 시각", example = "2026-07-20T18:00:00")
        LocalDateTime createdAt,
        @Schema(description = "수정 시각", example = "2026-07-20T18:30:00")
        LocalDateTime updatedAt
) {

    public static AdminTrustScoreInterventionRuleItem from(TrustScoreInterventionRule rule) {
        return new AdminTrustScoreInterventionRuleItem(
                rule.getId(),
                rule.getRuleName(),
                rule.getTriggerType(),
                rule.getActionType(),
                rule.isEnabled(),
                rule.getMinTrustScore(),
                rule.getMaxTrustScore(),
                rule.getMinSubmittedCount(),
                rule.getMinFalseReportCount(),
                rule.getDurationDays(),
                rule.getPriority(),
                rule.getReason(),
                rule.getCreatedAt(),
                rule.getUpdatedAt()
        );
    }
}
