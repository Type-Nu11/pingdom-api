package com.typenull.pingdom.boost.api.dto;

import java.util.List;

public record MerchantVerifiedBoostProductPageResponse(
        List<MerchantVerifiedBoostProductResponse> products,
        int page,
        int limit,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
    public static MerchantVerifiedBoostProductPageResponse from(VerifiedBoostProductPageResponse response) {
        return new MerchantVerifiedBoostProductPageResponse(
                response.products().stream().map(MerchantVerifiedBoostProductResponse::from).toList(),
                response.page(), response.limit(), response.totalElements(), response.totalPages(), response.hasNext());
    }
}
