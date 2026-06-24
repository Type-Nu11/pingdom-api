package com.typenull.pingdom.moderation.api.dto.audit;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "관리자 감사 로그 목록 응답")
public record AdminAuditLogResponse(
        List<AdminAuditLogItem> auditLogs,
        int page,
        int limit,
        long totalCount,
        int totalPages,
        boolean hasNext
) {
    public static AdminAuditLogResponse of(
            List<AdminAuditLogItem> auditLogs,
            int page,
            int limit,
            long totalCount,
            int totalPages
    ) {
        return new AdminAuditLogResponse(auditLogs, page, limit, totalCount, totalPages, page < totalPages);
    }
}
