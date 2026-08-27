package com.typenull.pingdom.moderation.api.dto;

import java.util.List;

public record DataQualityIssuePageResponse(
        List<DataQualityIssueResponse> items,
        int page,
        int limit,
        long total,
        int totalPages,
        boolean hasNext
) {
}
