package com.typenull.pingdom.domain.posts.dto;

public record PostUploadResponse(
        Long id,
        String url,
        String s3Key
) {
}

