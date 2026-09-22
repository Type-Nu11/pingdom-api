package com.typenull.pingdom.verification.api.dto;

import com.typenull.pingdom.verification.domain.CouponUsageStatus;
import com.typenull.pingdom.verification.domain.CrowdLevel;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 심사된 방문 제보의 정정 내용. 기존 제보 유형은 변경할 수 없고 해당 유형의 구조화 값만 제출.
 * 대기 시간은 분 단위 0~1,440이며 유형에 필요한 값 조합은 도메인에서 확인.
 */
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
    /** 언어 코드의 앞뒤 공백을 제거. null은 그대로 두어 유형별 필수 여부를 도메인에서 판단. */
    public VisitorVerificationReportCorrectionRequest {
        languageCode = languageCode == null ? null : languageCode.trim();
    }
}
