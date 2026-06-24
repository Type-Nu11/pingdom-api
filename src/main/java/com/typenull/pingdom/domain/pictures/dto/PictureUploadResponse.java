package com.typenull.pingdom.domain.pictures.dto;

public record PictureUploadResponse(
        Long id,
        String url,
        String s3Key
) {
}

