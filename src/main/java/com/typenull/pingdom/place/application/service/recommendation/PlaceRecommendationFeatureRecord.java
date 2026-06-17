package com.typenull.pingdom.place.application.service.recommendation;

import com.typenull.pingdom.place.domain.recommendation.PlaceRecommendationCandidateSource;

public record PlaceRecommendationFeatureRecord(
        Long placeId,
        PlaceRecommendationCandidateSource candidateSource,
        int ranking,
        long distanceMeters,
        double geoScore,
        double personalScore,
        double qualityScore,
        double engagementScore,
        double conversionScore,
        double explorationScore,
        double freshnessScore,
        double finalScore
) {
}
