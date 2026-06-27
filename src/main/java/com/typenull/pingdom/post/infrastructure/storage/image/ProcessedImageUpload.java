package com.typenull.pingdom.post.infrastructure.storage.image;

public record ProcessedImageUpload(
        byte[] originalBytes,
        String originalFilename,
        String contentType,
        byte[] thumbnailBytes,
        String thumbnailFilename,
        String thumbnailContentType,
        int width,
        int height,
        int thumbnailWidth,
        int thumbnailHeight
) {
}
