package com.typenull.pingdom.verification.api.dto;

import com.typenull.pingdom.verification.domain.CouponUsageStatus;
import com.typenull.pingdom.verification.domain.CrowdLevel;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VisitorVerificationReportCorrectionRequest(
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
    public VisitorVerificationReportCorrectionRequest {
        languageCode = languageCode == null ? null : languageCode.trim();
    }
}
