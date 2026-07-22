package com.typenull.pingdom.moderation.api.dto.notification;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "관리자 알림 목록 응답")
public record AdminNotificationResponse(
        @Schema(description = "관리자 알림 목록")
        List<AdminNotificationItem> notifications,
        @Schema(description = "현재 페이지 번호", example = "1")
        int page,
        @Schema(description = "페이지 크기", example = "20")
        int limit,
        @Schema(description = "전체 알림 개수", example = "1")
        long totalCount,
        @Schema(description = "전체 페이지 수", example = "1")
        int totalPages,
        @Schema(description = "다음 페이지 존재 여부", example = "false")
        boolean hasNext
) {
    public static AdminNotificationResponse of(
            List<AdminNotificationItem> notifications,
            int page,
            int limit,
            long totalCount,
            int totalPages
    ) {
        return new AdminNotificationResponse(notifications, page, limit, totalCount, totalPages, page < totalPages);
    }
}
