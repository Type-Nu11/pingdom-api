package com.typenull.pingdom.place.api.dto.ranking;

import java.time.Instant;
import java.util.List;

public record PlaceRankingResponse(
        PlaceRankingScope scope,
        PlaceRankingPeriod period,
        Instant periodStart,
        Instant periodEnd,
        String criteria,
        Instant generatedAt,
        Double requestedRadiusKm,
        Double appliedRadiusKm,
        boolean radiusExpanded,
        List<Item> items,
        int page,
        int limit,
        long totalCount,
        int totalPages,
        boolean hasNext
) {
    public record Item(
            int rank, Long placeId, String placeName, String category,
            Double latitude, Double longitude, Long distanceMeters,
            Double score, long likeCount, long postCount,
            String imageUrl, String thumbnailUrl, String imageSource,
            Long representativePostId, Long representativeMediaId,
            String registrantUsername, Boolean bookmarked
    ) {}
}
