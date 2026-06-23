package com.typenull.pingdom.moderation.api.dto.place.duplicate;

public record AdminMapPlaceMergeResponse(
        Long sourcePlaceId,
        Long targetPlaceId,
        String message
) {
}
