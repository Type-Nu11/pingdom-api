package com.typenull.pingdom.moderation.api.dto.trust;

import com.typenull.pingdom.engagement.domain.policy.TrustScoreInterventionAction;
import com.typenull.pingdom.engagement.domain.policy.TrustScoreInterventionTrigger;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminTrustScoreInterventionRuleRequest(
        @NotBlank(message = "ruleName은 필수입니다.")
        @Size(max = 100, message = "ruleName은 100자 이하여야 합니다.")
        @Schema(description = "규칙 이름", example = "low trust temporary restriction")
        String ruleName,

        @NotNull(message = "triggerType은 필수입니다.")
        @Schema(description = "트리거 유형", example = "FALSE_REPORT_COUNT")
        TrustScoreInterventionTrigger triggerType,

        @NotNull(message = "actionType은 필수입니다.")
        @Schema(description = "액션 유형", example = "TEMPORARY_RESTRICT")
        TrustScoreInterventionAction actionType,

        @Min(value = 0, message = "minTrustScore는 0 이상이어야 합니다.")
        @Max(value = 100, message = "minTrustScore는 100 이하여야 합니다.")
        @Schema(description = "최소 Trust Score", example = "0")
        int minTrustScore,

        @Min(value = 0, message = "maxTrustScore는 0 이상이어야 합니다.")
        @Max(value = 100, message = "maxTrustScore는 100 이하여야 합니다.")
        @Schema(description = "최대 Trust Score", example = "60")
        int maxTrustScore,

        @Min(value = 0, message = "minSubmittedCount는 0 이상이어야 합니다.")
        @Schema(description = "최소 신고 수", example = "3")
        long minSubmittedCount,

        @Min(value = 0, message = "minFalseReportCount는 0 이상이어야 합니다.")
        @Schema(description = "최소 허위 신고 수", example = "3")
        long minFalseReportCount,

        @Schema(description = "임시 제한 기간 일수. TEMPORARY_RESTRICT일 때만 입력합니다.", example = "7")
        Integer durationDays,

        @Min(value = 0, message = "priority는 0 이상이어야 합니다.")
        @Schema(description = "우선순위. 낮을수록 먼저 적용됩니다.", example = "10")
        int priority,

        @NotBlank(message = "reason은 필수입니다.")
        @Size(max = 500, message = "reason은 500자 이하여야 합니다.")
        @Schema(description = "개입 규칙 적용 사유", example = "허위 신고 누적 사용자 제한")
        String reason
) {
}
