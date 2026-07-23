package com.typenull.pingdom.moderation.api.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "관리자 대시보드 최근 장소 등록 항목")
public record AdminDashboardRecentPlaceItem(
        @Schema(description = "장소 ID", example = "10")
        Long placeId,
        @Schema(description = "장소명", example = "핑덤 카페")
        String name,
        @Schema(description = "주소", example = "서울특별시 중구 세종대로 110")
        String address,
        @Schema(description = "등록 사용자 ID", example = "3")
        Long userId,
        @Schema(description = "등록자명", example = "pingdom_user")
        String registrant,
        @Schema(description = "장소 등록일", example = "2026-07-21T15:30:00")
        LocalDateTime createdAt
) {
}
