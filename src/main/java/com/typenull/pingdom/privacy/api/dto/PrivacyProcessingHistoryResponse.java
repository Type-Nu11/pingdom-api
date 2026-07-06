package com.typenull.pingdom.privacy.api.dto;

import java.util.List;

public record PrivacyProcessingHistoryResponse(
        List<PrivacyProcessingHistoryItem> histories,
        int page,
        int limit,
        long totalCount,
        int totalPages,
        boolean hasNext
) {
    public static PrivacyProcessingHistoryResponse of(
            List<PrivacyProcessingHistoryItem> histories,
            int page,
            int limit,
            long totalCount,
            int totalPages
    ) {
        return new PrivacyProcessingHistoryResponse(histories, page, limit, totalCount, totalPages, page < totalPages);
    }
}
