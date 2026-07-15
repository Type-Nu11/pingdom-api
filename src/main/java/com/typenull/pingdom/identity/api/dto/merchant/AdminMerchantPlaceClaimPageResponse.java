package com.typenull.pingdom.identity.api.dto.merchant;

import java.util.List;

public record AdminMerchantPlaceClaimPageResponse(
        List<AdminMerchantPlaceClaimListItemResponse> claims,
        int page,
        int limit,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
}
