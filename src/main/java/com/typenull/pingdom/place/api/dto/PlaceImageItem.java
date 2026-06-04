package com.typenull.pingdom.place.api.dto;

import java.time.LocalDateTime;

public record PlaceImageItem(
        Long id,
        String imageUrl,
        String title,
        String description,
        LocalDateTime createdAt,
        long likeCount
) {
}
