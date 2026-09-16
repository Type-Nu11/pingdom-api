package com.typenull.pingdom.moderation.api.dto.community;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

public record AdminCommunityCommentPageResponse(
        List<Item> comments,
        int page,
        int limit,
        long totalCount,
        int totalPages,
        boolean hasNext
) {

    @Schema(name = "AdminCommunityCommentItem", description = "관리자 커뮤니티 댓글 목록 항목")
    public record Item(
            Long commentId,
            Long postId,
            Long authorUserId,
            String authorUsername,
            String content,
            boolean hidden,
            Long hiddenByAdminUserId,
            LocalDateTime hiddenAt,
            LocalDateTime createdAt
    ) {
    }
}
