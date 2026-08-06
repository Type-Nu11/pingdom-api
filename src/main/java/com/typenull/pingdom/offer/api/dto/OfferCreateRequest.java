package com.typenull.pingdom.offer.api.dto;

import com.typenull.pingdom.offer.domain.CouponEligibilityPolicy;
import com.typenull.pingdom.offer.domain.CouponExpiryPolicy;
import com.typenull.pingdom.offer.domain.CouponInventoryPolicy;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record OfferCreateRequest(
        @NotNull @Positive @Schema(example = "10") Long placeId,
        @NotBlank @Size(min = 1, max = 100) @Schema(example = "관광객 웰컴 음료", minLength = 1) String title,
        @NotBlank @Size(min = 1, max = 1000) @Schema(example = "여행 중인 관광객에게 제공하는 한정 Offer입니다.", minLength = 1) String description,
        @NotBlank @Size(min = 1, max = 500) @Schema(example = "음료 1잔 무료", minLength = 1) String benefitDescription,
        @NotNull @Schema(example = "2026-08-01T09:00:00") LocalDateTime startsAt,
        @NotNull @Schema(example = "2026-08-31T23:59:59") LocalDateTime endsAt,
        @Min(1) @Max(100000) @Schema(
                example = "100",
                nullable = true,
                description = "LIMITED 재고에서 필요한 발급 수량. UNLIMITED이면 생략합니다."
        ) Integer totalQuantity,
        @NotNull @Min(1) @Max(365) @Schema(example = "7") Integer couponValidityDays,
        @Schema(
                nullable = true,
                description = "쿠폰 발급 대상 정책. 생략하면 ACTIVE_TRAVEL_SCHEDULE입니다."
        ) CouponEligibilityPolicy eligibilityPolicy,
        @Schema(
                nullable = true,
                description = "쿠폰 재고 정책. 생략하면 LIMITED입니다."
        ) CouponInventoryPolicy inventoryPolicy,
        @Schema(
                nullable = true,
                description = "쿠폰 만료 정책. 생략하면 ISSUE_PLUS_DAYS_CAPPED_BY_OFFER_END입니다."
        ) CouponExpiryPolicy expiryPolicy
) {

    public OfferCreateRequest(
            Long placeId,
            String title,
            String description,
            String benefitDescription,
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            Integer totalQuantity,
            Integer couponValidityDays
    ) {
        this(
                placeId,
                title,
                description,
                benefitDescription,
                startsAt,
                endsAt,
                totalQuantity,
                couponValidityDays,
                null,
                null,
                null
        );
    }
}
