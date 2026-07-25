package com.typenull.pingdom.boost.api.dto;

import com.typenull.pingdom.boost.domain.VerifiedBoostProduct;
import com.typenull.pingdom.boost.domain.VerifiedBoostProductStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record VerifiedBoostProductResponse(
        @Schema(example = "1") Long id,
        @Schema(example = "10") Long placeId,
        String name,
        String description,
        @Schema(example = "30000", description = "KRW 기준 가격") long priceAmount,
        @Schema(example = "7") int durationDays,
        VerifiedBoostProductStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static VerifiedBoostProductResponse from(VerifiedBoostProduct product) {
        return new VerifiedBoostProductResponse(
                product.getId(), product.getPlaceId(), product.getName(), product.getDescription(),
                product.getPriceAmount(), product.getDurationDays(), product.getStatus(),
                product.getCreatedAt(), product.getUpdatedAt());
    }
}
