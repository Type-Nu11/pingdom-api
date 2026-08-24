package com.typenull.pingdom.offer.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record CouponPageResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<CouponResponse> coupons,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int page,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int limit,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long totalElements,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int totalPages,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean hasNext
) {
}
