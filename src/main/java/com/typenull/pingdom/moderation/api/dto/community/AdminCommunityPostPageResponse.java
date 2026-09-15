package com.typenull.pingdom.moderation.api.dto.community;

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
