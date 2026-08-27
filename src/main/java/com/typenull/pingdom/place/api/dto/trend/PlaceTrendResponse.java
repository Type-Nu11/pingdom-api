package com.typenull.pingdom.place.api.dto.trend;

import java.time.LocalDateTime;
import java.util.List;

public record PlaceTrendResponse(
        String scope,
        PlaceTrendPeriod period,
        LocalDateTime periodStart,
        LocalDateTime periodEnd,
        LocalDateTime generatedAt,
        List<Item> places,
        int page,
        int limit,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
    public record Item(
            int rank,
            Long placeId,
            String placeName,
            String category,
            String imageUrl,
            String address,
            long bookmarkAdds,
            long bookmarkRemoves,
            long netBookmarkGrowth,
            long bookmarkCount,
            boolean bookmarked
    ) {
    }
}
