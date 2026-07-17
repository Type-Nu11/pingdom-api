package com.typenull.pingdom.offer.api.dto;

import java.util.List;

public record OfferPageResponse(
        List<OfferResponse> offers,
        int page,
        int limit,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
}
