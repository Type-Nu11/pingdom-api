package com.typenull.pingdom.boost.api.dto;

import java.util.List;

public record VerifiedBoostExecutionPageResponse(
        List<VerifiedBoostExecutionResponse> executions,
        int page,
        int limit,
        long totalCount,
        int totalPages,
        boolean hasNext
) {
}
