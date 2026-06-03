package com.typenull.pingdom.engagement.api.dto;

public record MapImageLikeResponse(
        long userId,
        long mapImageId,
        String message
) {
}
