package com.typenull.pingdom.place.api.dto.ranking;

import java.time.Instant;
import java.util.List;

/**
 * 기간별 장소 순위와 요청·실제 적용 반경 정보를 함께 반환.
 * 전국 범위의 거리·반경은 null이며 비로그인 회원의 bookmarked도 false 대신 null로 표현.
 */
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
