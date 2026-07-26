package com.typenull.pingdom.campaign.api.dto;

import java.util.List;

public record BrandPageResponse(
        List<BrandResponse> items,
        int page,
        int limit,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
}
