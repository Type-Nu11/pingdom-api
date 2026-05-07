package com.typenull.pingdom.domain.admin.dto.picture;

import java.time.LocalDateTime;

public record AdminPictureResponse(
        Long id,
        String url,
        String s3Key,
        LocalDateTime createdAt
) {
}

