package com.typenull.pingdom.verification.api.dto;

import com.typenull.pingdom.verification.domain.VisitorVerificationReportType;
import jakarta.validation.constraints.*;

public record VisitorVerificationReportCreateRequest(
        @NotNull Long placeId,
        @NotNull VisitorVerificationReportType reportType,
        @NotBlank @Size(max = 1000) String description,
        @Size(max = 500)
        @Pattern(regexp = "^https://[^\\s]+$", message = "증빙 URL은 HTTPS 형식이어야 합니다.") String evidenceUrl
) {}
