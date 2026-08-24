package com.typenull.pingdom.offer.api.dto;

import com.typenull.pingdom.offer.domain.CouponStatus;
import com.typenull.pingdom.offer.domain.TouristCoupon;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record CouponResponse(
        @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED) Long id,
        @Schema(example = "1", requiredMode = Schema.RequiredMode.REQUIRED) Long offerId,
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
                coupon.getCode(),
                coupon.statusAt(now),
                coupon.getIssuedAt(),
                coupon.getExpiresAt(),
                coupon.getRedeemedAt()
        );
    }
}
