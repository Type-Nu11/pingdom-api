package com.typenull.pingdom.place.api.dto.registration;

import java.util.List;

public record AdminMerchantPlaceApplicationPageResponse(
        List<AdminMerchantPlaceApplicationListItemResponse> items,
        int page,
        int limit,
        long total,
        int totalPages,
        boolean hasNext
) {
}
