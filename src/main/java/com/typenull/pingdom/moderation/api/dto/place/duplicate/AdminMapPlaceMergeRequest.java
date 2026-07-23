package com.typenull.pingdom.moderation.api.dto.place.duplicate;

public record AdminMapPlaceMergeRequest(
        Long sourcePlaceId,
        Long targetPlaceId,
        Long candidateId
) {

    public AdminMapPlaceMergeRequest(Long sourcePlaceId, Long targetPlaceId) {
        this(sourcePlaceId, targetPlaceId, null);
    }
}
