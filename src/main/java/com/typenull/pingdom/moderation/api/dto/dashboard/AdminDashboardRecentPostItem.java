package com.typenull.pingdom.moderation.api.dto.dashboard;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 대시보드 최근 게시글 등록 항목")
public record AdminDashboardRecentPostItem(
        @Schema(description = "게시글 ID", example = "10")
        Long postId,
        @Schema(description = "게시글 제목", example = "야경이 좋은 장소")
        String title,
        @Schema(description = "작성자 ID", example = "3")
        Long userId,
        @Schema(description = "작성자명", example = "pingdom_user")
        String username,
        @Schema(description = "장소 ID", example = "7")
        Long placeId,
        @Schema(description = "장소명", example = "핑덤 카페")
        String placeName,
        @Schema(description = "생성일", example = "2026-07-21T15:30:00")
        LocalDateTime createdAt
) {
}
