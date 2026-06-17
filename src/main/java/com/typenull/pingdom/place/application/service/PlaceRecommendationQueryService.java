package com.typenull.pingdom.place.application.service;

import com.typenull.pingdom.place.api.dto.recommendation.PlaceRecommendationResponse;

public interface PlaceRecommendationQueryService {
    PlaceRecommendationResponse recommendPlaces(
            Long userId,
            double latitude,
            double longitude,
            int limit,
            double radiusKm,
            String requestedRecommendationVersion
    );
}
