package com.typenull.pingdom.post.api.dto;

public record MapImageUploadResponse(
        Long id,
        String imageUrl,
        String s3Key,
        String message
) {
}
