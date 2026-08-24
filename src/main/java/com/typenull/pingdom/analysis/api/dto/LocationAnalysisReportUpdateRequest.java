package com.typenull.pingdom.analysis.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LocationAnalysisReportUpdateRequest(
        @NotBlank(message = "보고서명은 필수입니다.") String reportName,
        @NotBlank(message = "이메일은 필수입니다.") @Email(message = "이메일 형식이 올바르지 않습니다.") String email
) {
}
