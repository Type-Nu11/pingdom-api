package com.typenull.pingdom.moderation.api.dto.trust;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record AdminTrustScoreInterventionRuleResponse(
        @Schema(description = "개입 규칙 목록")
        List<AdminTrustScoreInterventionRuleItem> rules
) {
}
