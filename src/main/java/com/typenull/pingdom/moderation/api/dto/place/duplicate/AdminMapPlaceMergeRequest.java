package com.typenull.pingdom.moderation.api.dto.place.duplicate;

public record AdminMapPlaceMergeRequest(
        Long sourcePlaceId,
        Long targetPlaceId
) {
}
