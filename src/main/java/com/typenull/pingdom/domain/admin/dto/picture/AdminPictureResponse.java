package com.typenull.pingdom.domain.admin.dto.picture;

public record AdminPictureResponse(
        Long id,
        String imageUrl,
        String s3Key,
        Long userId
) {
}
