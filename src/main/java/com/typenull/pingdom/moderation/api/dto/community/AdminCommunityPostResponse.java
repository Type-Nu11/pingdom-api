package com.typenull.pingdom.moderation.api.dto.community;

import java.time.LocalDateTime;
import java.util.List;

public record AdminCommunityPostResponse(
        Long postId,
        String categoryId,
        String title,
        String content,
        Long authorUserId,
        String authorUsername,
        boolean hidden,
        Long hiddenByAdminUserId,
        LocalDateTime hiddenAt,
        LocalDateTime createdAt,
        List<Place> places
) {

    public record Place(Long placeId, String name, boolean deleted) {
    }
}
