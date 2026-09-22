package com.typenull.pingdom.verification.api.dto;

import jakarta.validation.constraints.Size;

/** 프로필 심사의 사유. 승인은 생략 가능하며 정지·회수에는 도메인에서 사유를 요구. */
public record ScoutProfileReviewRequest(
        @Size(max = 500) String reason
) {
}
