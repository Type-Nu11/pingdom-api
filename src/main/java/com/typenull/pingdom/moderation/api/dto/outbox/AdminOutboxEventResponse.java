package com.typenull.pingdom.moderation.api.dto.outbox;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "관리자 Outbox 이벤트 운영 조회 응답")
public record AdminOutboxEventResponse(
        List<AdminOutboxEventItem> events,
        int page,
        int limit,
        long totalCount,
        int totalPages,
        boolean hasNext
) {
    public static AdminOutboxEventResponse of(
            List<AdminOutboxEventItem> events,
            int page,
            int limit,
            long totalCount,
            int totalPages
    ) {
        return new AdminOutboxEventResponse(events, page, limit, totalCount, totalPages, page < totalPages);
    }
}
