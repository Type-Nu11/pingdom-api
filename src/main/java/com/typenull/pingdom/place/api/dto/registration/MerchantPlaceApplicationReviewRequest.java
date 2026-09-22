package com.typenull.pingdom.place.api.dto.registration;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * 관리자가 확인한 신청의 JPA version과 심사 사유.
 * reviewedVersion은 제출 횟수인 submissionVersion이 아니며 불일치 시 오래된 심사 요청으로 거절됨.
 */
public record MerchantPlaceApplicationReviewRequest(
        @NotNull @PositiveOrZero Long reviewedVersion,
        @Size(max = 500) String reason
) {
}
