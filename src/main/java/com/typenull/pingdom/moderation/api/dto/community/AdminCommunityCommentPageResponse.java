package com.typenull.pingdom.moderation.api.dto.community;

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
