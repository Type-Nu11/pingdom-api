package com.typenull.pingdom.moderation.api.dto.post;

import java.time.LocalDateTime;
import java.util.List;
import com.typenull.pingdom.post.domain.MapImageVisibilityStatus;

public record AdminPostItem(
        Long id,
        String name,
        String imageUrl,
        String thumbnailUrl,
        Long userId,
        String username,
        LocalDateTime createdAt,
        String description,
        Long likeCount,
        String placeName,
        MapImageVisibilityStatus visibilityStatus,
        LocalDateTime hiddenAt,
        String hiddenReason,
        List<AdminPostReportItem> reports
) {}
