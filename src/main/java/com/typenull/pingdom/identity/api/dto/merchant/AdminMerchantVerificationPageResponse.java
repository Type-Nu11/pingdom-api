package com.typenull.pingdom.identity.api.dto.merchant;

import java.util.List;

public record AdminMerchantVerificationPageResponse(
        List<AdminMerchantVerificationListItemResponse> verifications,
        int page,
        int limit,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
}
