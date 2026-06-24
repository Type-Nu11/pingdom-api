package com.typenull.pingdom.moderation.api.dto.place.duplicate;

public record AdminPlaceMergeRestoreResponse(
        Long historyId,
        Long sourcePlaceId,
        Long targetPlaceId,
        String message
) {
}
