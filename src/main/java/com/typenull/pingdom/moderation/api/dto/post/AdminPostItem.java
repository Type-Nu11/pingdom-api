package com.typenull.pingdom.moderation.api.dto.post;

import java.time.LocalDateTime;
import java.util.List;

public record AdminPostItem(
        Long id,
        String name,
        String thumbnailUrl,
        String imageUrl,
        Long userId,
        String username,
        LocalDateTime createdAt,
        String description,
        Long likeCount,
        String placeName,
        List<AdminPostReportItem> reports
) {}
