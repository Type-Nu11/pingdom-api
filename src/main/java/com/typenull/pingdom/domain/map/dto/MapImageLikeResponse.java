package com.typenull.pingdom.domain.map.dto;

public record MapImageLikeResponse(
        long userId,
        long mapImageId,
        String message
) {
}
