package com.typenull.pingdom.verification.api.dto;

import com.typenull.pingdom.verification.domain.VisitorVerificationReportType;
import com.typenull.pingdom.verification.domain.CouponUsageStatus;
import com.typenull.pingdom.verification.domain.CrowdLevel;
import jakarta.validation.constraints.*;

public record VisitorVerificationReportCreateRequest(
        @NotNull Long placeId,
        @NotNull VisitorVerificationReportType reportType,
        @NotBlank @Size(max = 1000) String description,
        @Size(max = 500)
        @Pattern(regexp = "^https://[^\\s]+$", message = "증빙 URL은 HTTPS 형식이어야 합니다.") String evidenceUrl,
        @Min(0) @Max(1440) Integer waitTimeMinutes,
        @Size(max = 10)
        @Pattern(regexp = "^[a-z]{2,3}(-[A-Z]{2})?$", message = "언어 코드는 ISO 형식이어야 합니다.")
        String languageCode,
        CouponUsageStatus couponUsageStatus,
        CrowdLevel crowdLevel
) {
    public VisitorVerificationReportCreateRequest {
        languageCode = languageCode == null ? null : languageCode.trim();
    }
}
