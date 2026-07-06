package com.typenull.pingdom.place.event;

import java.util.List;

public record PlaceRecommendationExposureRecordRequestedEvent(
        Long userId,
        double latitude,
        double longitude,
        String requestId,
        List<Long> placeIds,
        String recommendationVersion
) {
}
