package com.typenull.pingdom.moderation.api.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "관리자 사용자 제재 이력 목록 응답")
public record AdminUserSanctionHistoryResponse(
        List<AdminUserSanctionHistoryItem> histories,
        int page,
        int limit,
        long totalCount,
        int totalPages,
        boolean hasNext
) {
    public static AdminUserSanctionHistoryResponse of(
            List<AdminUserSanctionHistoryItem> histories,
            int page,
            int limit,
            long totalCount,
            int totalPages
    ) {
        int normalizedTotalPages = Math.max(totalPages, 1);
        return new AdminUserSanctionHistoryResponse(
                histories,
                page,
                limit,
                totalCount,
                normalizedTotalPages,
                page < normalizedTotalPages
        );
    }
}
