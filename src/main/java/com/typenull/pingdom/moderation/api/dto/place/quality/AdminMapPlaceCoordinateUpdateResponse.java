package com.typenull.pingdom.moderation.api.dto.place.quality;

public record AdminMapPlaceCoordinateUpdateResponse(
        Long placeId,
        Double latitude,
        Double longitude,
        String message
) {
}
