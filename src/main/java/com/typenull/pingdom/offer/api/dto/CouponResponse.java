package com.typenull.pingdom.offer.api.dto;

import com.typenull.pingdom.offer.domain.CouponStatus;
import com.typenull.pingdom.offer.domain.TouristCoupon;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record CouponResponse(
        @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED) Long id,
        @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED) Long offerId,
        @Schema(example = "관광객 웰컴 음료", nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) String offerTitle,
        @Schema(example = "음료 1잔 무료", nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) String benefitDescription,
        @Schema(example = "1", nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) Long placeId,
        @Schema(example = "핑덤 카페", nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) String placeName,
        @Schema(example = "3fa85f64-5717-4562-b3fc-2c963f66afa6", requiredMode = Schema.RequiredMode.REQUIRED) String code,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) CouponStatus status,
        @Schema(format = "date-time", requiredMode = Schema.RequiredMode.REQUIRED) LocalDateTime issuedAt,
        @Schema(format = "date-time", requiredMode = Schema.RequiredMode.REQUIRED) LocalDateTime expiresAt,
        @Schema(format = "date-time", nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) LocalDateTime redeemedAt
) {
    public static CouponResponse from(TouristCoupon coupon, LocalDateTime now) {
        return new CouponResponse(
                coupon.getId(),
                coupon.getOfferId(),
                coupon.getOfferTitle(),
                coupon.getBenefitDescription(),
                coupon.getPlaceId(),
                coupon.getPlaceName(),
                coupon.getCode(),
                coupon.statusAt(now),
                coupon.getIssuedAt(),
                coupon.getExpiresAt(),
                coupon.getRedeemedAt()
        );
    }
}
