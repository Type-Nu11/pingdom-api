package com.typenull.pingdom.moderation.api.dto.appeal;

import jakarta.validation.constraints.Size;

public record AdminReportAppealActionRequest(
        @Size(max = 500, message = "처리 사유는 500자 이하여야 합니다.")
        String reason
) {
}
