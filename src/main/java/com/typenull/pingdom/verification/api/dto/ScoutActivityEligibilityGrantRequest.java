package com.typenull.pingdom.verification.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * 활동 자격의 시작과 선택 종료 시각을 지정한다. 종료 null은 무기한이며 기간 순서는 도메인이 검사한다.
 * reason은 감사 기록용 선택 사유다.
 */
public record ScoutActivityEligibilityGrantRequest(
        @NotNull LocalDateTime eligibleFrom,
        LocalDateTime eligibleUntil,
        @Size(max = 500) String reason
) {
}
