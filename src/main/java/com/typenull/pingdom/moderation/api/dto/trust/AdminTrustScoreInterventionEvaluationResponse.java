package com.typenull.pingdom.moderation.api.dto.trust;

import com.typenull.pingdom.engagement.domain.policy.TrustScoreInterventionAction;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record AdminTrustScoreInterventionEvaluationResponse(
        @Schema(description = "신고자 사용자 ID", example = "12")
        Long reporterUserId,
        @Schema(description = "현재 Trust Score", example = "40")
        int trustScore,
        @Schema(description = "매칭된 규칙 ID", example = "1")
        Long matchedRuleId,
        @Schema(description = "매칭된 규칙 이름", example = "low trust temporary restriction")
        String matchedRuleName,
        @Schema(description = "적용 액션", example = "TEMPORARY_RESTRICT")
        TrustScoreInterventionAction actionType,
        @Schema(description = "제한 만료 시각", example = "2026-07-27T18:00:00")
        LocalDateTime restrictedUntil,
        @Schema(description = "처리 결과 메시지", example = "Trust Score 개입 규칙을 적용했습니다.")
        String message
) {
}
