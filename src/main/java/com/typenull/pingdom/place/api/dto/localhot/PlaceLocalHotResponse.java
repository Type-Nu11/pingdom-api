package com.typenull.pingdom.place.api.dto.localhot;

import java.util.List;

public record PlaceLocalHotResponse(
        Region region,
        List<Item> places,
        int page,
        int limit,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
    public record Region(
            String regionCode,
            String sido,
            String sigungu,
            String regionName
    ) {
    }

    public record Item(
            int rank,
            Long placeId,
            String placeName,
            String category,
            String address,
            Double latitude,
            Double longitude,
            String imageUrl,
            long bookmarkCount,
            boolean bookmarked
    ) {
    }
}
