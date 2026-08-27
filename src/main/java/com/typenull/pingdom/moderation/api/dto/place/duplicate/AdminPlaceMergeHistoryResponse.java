package com.typenull.pingdom.moderation.api.dto.place.duplicate;

import java.util.List;

public record AdminPlaceMergeHistoryResponse(
        List<AdminPlaceMergeHistoryItem> histories,
        int page,
        int limit,
        long total,
        int totalPages,
        boolean hasNext
) {
}
