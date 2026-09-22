package com.typenull.pingdom.verification.api.dto;

import com.typenull.pingdom.verification.domain.VisitorVerificationReportType;
import com.typenull.pingdom.verification.domain.CouponUsageStatus;
import com.typenull.pingdom.verification.domain.CrowdLevel;
import jakarta.validation.constraints.*;

/**
 * 관광객 제보의 장소·유형·본문과 선택 증빙/구조화 값.
 * 대기 시간은 분 단위이며 유형별 필수 값과 다른 유형 값의 혼합 금지는 도메인이 검사.
 */
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
    /** 언어 코드의 앞뒤 공백을 제거. null은 그대로 두어 유형별 필수 여부를 도메인에서 판단. */
    public VisitorVerificationReportCreateRequest {
        languageCode = languageCode == null ? null : languageCode.trim();
    }
}
