package com.typenull.pingdom.moderation.api.dto.community;

import java.time.LocalDateTime;

public record AdminCommunityCommentResponse(
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
