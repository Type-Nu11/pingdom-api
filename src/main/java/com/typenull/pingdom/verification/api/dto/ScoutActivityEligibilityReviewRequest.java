package com.typenull.pingdom.verification.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 활동 자격의 정지·회수에 사용할 필수 사유로 공백은 허용하지 않는다. */
public record ScoutActivityEligibilityReviewRequest(
        @NotBlank @Size(max = 500) String reason
) {
}
