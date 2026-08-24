package com.typenull.pingdom.boost.api.dto;

import com.typenull.pingdom.boost.domain.VerifiedBoostProductStatus;
import io.swagger.v3.oas.annotations.media.Schema;

public record MerchantVerifiedBoostProductResponse(
        @Schema(example = "1") Long productId,
        String name,
        String description,
        @Schema(example = "30000") long priceAmount,
        @Schema(example = "KRW") String currency,
        @Schema(example = "7") int durationDays,
        VerifiedBoostProductStatus status
) {
    public static MerchantVerifiedBoostProductResponse from(VerifiedBoostProductResponse response) {
        return new MerchantVerifiedBoostProductResponse(
                response.id(), response.name(), response.description(), response.priceAmount(), "KRW",
                response.durationDays(), response.status());
    }
}
