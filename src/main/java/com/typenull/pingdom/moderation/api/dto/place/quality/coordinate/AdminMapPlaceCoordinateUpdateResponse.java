package com.typenull.pingdom.moderation.api.dto.place.quality.coordinate;

public record AdminMapPlaceCoordinateUpdateResponse(
        Long placeId,
        Double latitude,
        Double longitude,
        String message
) {
}
