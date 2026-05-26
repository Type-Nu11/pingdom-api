package com.typenull.pingdom.domain.admin.dto.picture;

import java.time.LocalDateTime;

public record AdminPictureItem(
        Long id,
        String thumbnailUrl,
        String imageUrl,
        Long userId,
        String username,
        LocalDateTime createdAt
) {}