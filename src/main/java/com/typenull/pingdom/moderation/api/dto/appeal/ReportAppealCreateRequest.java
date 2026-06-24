package com.typenull.pingdom.moderation.api.dto.appeal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReportAppealCreateRequest(
        @NotNull(message = "신고 ID는 필수입니다.")
        Long reportId,
        @NotBlank(message = "이의제기 사유는 필수입니다.")
        @Size(max = 500, message = "이의제기 사유는 500자 이하여야 합니다.")
        String reason
) {
}
