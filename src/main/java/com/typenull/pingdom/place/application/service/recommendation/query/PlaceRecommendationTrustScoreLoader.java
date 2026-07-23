package com.typenull.pingdom.place.application.service.recommendation.query;

import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationTrustScoreRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class PlaceRecommendationTrustScoreLoader {

    static final double NEUTRAL_TRUST_SCORE = 0.5d;

    private final PlaceRecommendationTrustScoreRepository trustScoreRepository;

    Map<Long, Double> load(List<PlaceDistance> candidates) {
        if (candidates.isEmpty()) {
            return Map.of();
        }

        List<Long> placeIds = candidates.stream()
                .map(candidate -> candidate.place().getId())
                .distinct()
                .toList();

        return trustScoreRepository.findTrustScoresByPlaceIds(placeIds).stream()
                .collect(Collectors.toUnmodifiableMap(
                        PlaceRecommendationTrustScoreRepository.PlaceTrustScoreProjection::getPlaceId,
                        projection -> clamp(projection.getTrustScore())
                ));
    }

    private double clamp(Double score) {
        if (score == null) {
            return NEUTRAL_TRUST_SCORE;
        }
        return Math.max(0d, Math.min(1d, score));
    }
}
