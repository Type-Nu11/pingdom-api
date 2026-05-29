package com.typenull.pingdom.domain.admin.dto.picture;

import java.time.LocalDateTime;
import java.util.List;

public record AdminPictureItem(
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
        List<AdminPictureReportItem> reports
) {}
