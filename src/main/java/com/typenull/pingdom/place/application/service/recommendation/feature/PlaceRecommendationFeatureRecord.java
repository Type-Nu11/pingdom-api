package com.typenull.pingdom.place.application.service.recommendation.feature;

import com.typenull.pingdom.place.domain.recommendation.candidate.PlaceRecommendationCandidateSource;

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
