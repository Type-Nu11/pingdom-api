package com.typenull.pingdom.moderation.api.dto.place;

public record AdminPlaceRecommendationSnapshotResyncResponse(
        long placeCount,
        long synchronizedSnapshotCount,
        long deletedSnapshotCount,
        long synchronizedVersionSnapshotCount,
        long deletedVersionSnapshotCount,
        String message
) {
}
