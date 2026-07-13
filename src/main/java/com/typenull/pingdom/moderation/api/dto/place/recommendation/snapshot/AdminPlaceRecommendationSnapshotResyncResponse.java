package com.typenull.pingdom.moderation.api.dto.place.recommendation.snapshot;

public record AdminPlaceRecommendationSnapshotResyncResponse(
        long placeCount,
        long synchronizedSnapshotCount,
        long deletedSnapshotCount,
        long synchronizedSimilaritySnapshotCount,
        long deletedSimilaritySnapshotCount,
        long synchronizedVersionSnapshotCount,
        long deletedVersionSnapshotCount,
        String message
) {
}
