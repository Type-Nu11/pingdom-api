package com.typenull.pingdom.domain.admin.dto.place;

import java.time.LocalDateTime;

public record AdminMapPlaceImageItem(
        Long id,
        String imageUrl,
        String title,
        String description,
        Long userId,
        String username,
        LocalDateTime createdAt,
        long likeCount
) {
}
