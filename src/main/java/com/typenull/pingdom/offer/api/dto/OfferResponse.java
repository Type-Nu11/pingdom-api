package com.typenull.pingdom.offer.api.dto;

import com.typenull.pingdom.offer.domain.CouponEligibilityPolicy;
import com.typenull.pingdom.offer.domain.CouponExpiryPolicy;
import com.typenull.pingdom.offer.domain.CouponInventoryPolicy;
import com.typenull.pingdom.offer.domain.OfferStatus;
import com.typenull.pingdom.offer.domain.TouristOffer;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record OfferResponse(
        @Schema(example = "1") Long id,
        @Schema(example = "10") Long placeId,
        @Schema(example = "관광객 웰컴 음료") String title,
        String description,
        @Schema(example = "음료 1잔 무료") String benefitDescription,
        OfferStatus status,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        @Schema(nullable = true, description = "LIMITED 재고의 총 발급 수량. UNLIMITED이면 null") Integer totalQuantity,
        int issuedQuantity,
        @Schema(nullable = true, description = "남은 발급 수량. UNLIMITED이면 null") Integer remainingQuantity,
        int couponValidityDays,
        CouponEligibilityPolicy eligibilityPolicy,
        CouponInventoryPolicy inventoryPolicy,
        CouponExpiryPolicy expiryPolicy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static OfferResponse from(TouristOffer offer) {
        return new OfferResponse(
                offer.getId(),
                offer.getPlaceId(),
                offer.getTitle(),
                offer.getDescription(),
                offer.getBenefitDescription(),
                offer.getStatus(),
                offer.getStartsAt(),
                offer.getEndsAt(),
                offer.getTotalQuantity(),
                offer.getIssuedQuantity(),
                offer.getTotalQuantity() == null
                        ? null
                        : Math.max(offer.getTotalQuantity() - offer.getIssuedQuantity(), 0),
                offer.getCouponValidityDays(),
                offer.getEligibilityPolicy(),
                offer.getInventoryPolicy(),
                offer.getExpiryPolicy(),
                offer.getCreatedAt(),
                offer.getUpdatedAt()
        );
    }
}
