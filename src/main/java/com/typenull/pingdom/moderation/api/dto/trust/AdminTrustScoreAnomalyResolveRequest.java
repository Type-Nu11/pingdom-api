package com.typenull.pingdom.moderation.api.dto.trust;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminTrustScoreAnomalyResolveRequest(
        @NotBlank(message = "resolutionReason은 필수입니다.")
        @Size(max = 500, message = "resolutionReason은 500자 이하여야 합니다.")
        @Schema(description = "이상치 해결 사유", example = "관리자 검토 후 정상 패턴으로 확인했습니다.")
        String resolutionReason
) {
}
