package com.typenull.pingdom.moderation.api.dto.report;

import java.util.List;

public record ReportedUsersResponse (
        List<ReportedUsersItem> users,
        int page,
        int limit,
        long totalCount,
        long totalPages,
        boolean hasNext
) {
    public static ReportedUsersResponse of(List<ReportedUsersItem> users, int page, int limit, long totalCount, long totalPages) {
        return new ReportedUsersResponse(users, page, limit, totalCount, totalPages, page < totalPages);
    }
}

