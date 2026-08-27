package com.typenull.pingdom.moderation.api.dto.trust;

import java.util.List;

public record AdminTrustScoreChangeHistoryResponse(
        List<AdminTrustScoreChangeHistoryItem> items,
        int page,
        int limit,
        long total,
        int totalPages,
        boolean hasNext
) {
}
