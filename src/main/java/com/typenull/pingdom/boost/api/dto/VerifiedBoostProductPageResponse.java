package com.typenull.pingdom.boost.api.dto;

import java.util.List;

public record VerifiedBoostProductPageResponse(
        List<VerifiedBoostProductResponse> products,
        int page,
        int limit,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
}
