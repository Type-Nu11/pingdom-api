package com.typenull.pingdom.domain.map.dto;

public record MapImageUploadResponse(
        Long id,
        String imageUrl,
        String s3Key,
        String message
) {
}

