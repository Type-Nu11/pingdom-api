package com.typenull.pingdom.moderation.api.dto.place;

public record AdminMapPlaceMergeRequest(
        Long sourcePlaceId,
        Long targetPlaceId
) {
}
