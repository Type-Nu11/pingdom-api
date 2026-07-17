package com.typenull.pingdom.offer.api.dto;

import java.util.List;

public record CouponPageResponse(
        List<CouponResponse> coupons,
        int page,
        int limit,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
}
