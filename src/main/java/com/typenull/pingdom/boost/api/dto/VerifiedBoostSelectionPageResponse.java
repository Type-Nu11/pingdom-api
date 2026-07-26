package com.typenull.pingdom.boost.api.dto;

import java.util.List;

public record VerifiedBoostSelectionPageResponse(
        List<VerifiedBoostSelectionResponse> selections,
        int page,
        int limit,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
}
