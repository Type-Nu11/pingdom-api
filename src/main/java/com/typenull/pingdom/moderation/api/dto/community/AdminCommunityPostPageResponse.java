package com.typenull.pingdom.moderation.api.dto.community;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

public record AdminCommunityPostPageResponse(
        List<Item> posts,
        int page,
        int limit,
        long totalCount,
        int totalPages,
        boolean hasNext
) {

    @Schema(name = "AdminCommunityPostItem", description = "관리자 커뮤니티 글 목록 항목")
    public record Item(
            Long postId,
            String categoryId,
            String title,
            Long authorUserId,
            String authorUsername,
            boolean hidden,
            Long hiddenByAdminUserId,
            LocalDateTime hiddenAt,
            LocalDateTime createdAt
    ) {
    }
}
