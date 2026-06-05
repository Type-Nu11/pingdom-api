package com.typenull.pingdom.engagement.api.dto.like;

public record MapImageLikeResponse(
        long userId,
        long mapImageId,
        String message
) {
}
