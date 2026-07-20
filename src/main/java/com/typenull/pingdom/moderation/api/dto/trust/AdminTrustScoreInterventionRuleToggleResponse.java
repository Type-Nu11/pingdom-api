package com.typenull.pingdom.moderation.api.dto.trust;

import io.swagger.v3.oas.annotations.media.Schema;

public record AdminTrustScoreInterventionRuleToggleResponse(
        @Schema(description = "개입 규칙 ID", example = "1")
        Long ruleId,
        @Schema(description = "활성화 여부", example = "false")
        boolean enabled,
        @Schema(description = "처리 결과 메시지", example = "Trust Score 개입 규칙을 비활성화했습니다.")
        String message
) {
}
