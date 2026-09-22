package com.typenull.pingdom.verification.api.dto;

import com.typenull.pingdom.verification.domain.VisitorVerificationReportCorrectionStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 정정 심사 결과는 승인·거절 두 가지로 제한. 거절 사유 필수 조건은 도메인이 확인. */
public record VisitorVerificationReportCorrectionReviewRequest(
        @NotNull Decision decision,
        @Size(max = 500) String reviewNote
) {
    public enum Decision {
        ACCEPTED,
        REJECTED;

        /** 요청에서 허용한 승인·거절 enum 이름을 동일한 도메인 상태로 변환. */
        public VisitorVerificationReportCorrectionStatus toStatus() {
            return VisitorVerificationReportCorrectionStatus.valueOf(name());
        }
    }
}
