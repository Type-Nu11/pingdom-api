package com.typenull.pingdom.moderation.api.dto.place;

public record AdminMapPlaceMergeResponse(
        Long sourcePlaceId,
        Long targetPlaceId,
        String message
) {
}
