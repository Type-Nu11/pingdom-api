package com.typenull.pingdom.moderation.api.dto.appeal;

import java.util.List;

public record AdminReportAppealResponse(
        List<AdminReportAppealItem> appeals,
        int page,
        int limit,
        long totalCount,
        int totalPages,
        boolean hasNext
) {
    public static AdminReportAppealResponse of(
            List<AdminReportAppealItem> appeals,
            int page,
            int limit,
            long totalCount,
            int totalPages
    ) {
        return new AdminReportAppealResponse(appeals, page, limit, totalCount, totalPages, page < totalPages);
    }
}
