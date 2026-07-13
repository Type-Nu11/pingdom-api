package com.typenull.pingdom.identity.api.dto.merchant;

import java.util.List;

public record MerchantOwnerProfilePageResponse(
        List<MerchantOwnerProfileResponse> profiles,
        int page,
        int limit,
        long totalCount,
        int totalPages,
        boolean hasNext
) {
}
