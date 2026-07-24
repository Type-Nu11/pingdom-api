package com.typenull.pingdom.place.application.service.recommendation.query;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
class PlaceRecommendationCommerceRankingService {

    List<ScoredCandidate> apply(
            List<ScoredCandidate> candidates,
            Map<Long, PlaceRecommendationCommerceSignalLoader.CommerceSignal> signalsByPlaceId,
            double benefitBoost,
            double availabilityBoost
    ) {
        if (candidates.isEmpty() || (benefitBoost <= 0d && availabilityBoost <= 0d)) {
            return candidates;
        }

        return candidates.stream()
                .map(candidate -> applyBoost(
                        candidate,
                        signalsByPlaceId.getOrDefault(
                                candidate.place().getId(),
                                PlaceRecommendationCommerceSignalLoader.CommerceSignal.NONE
                        ),
                        benefitBoost,
                        availabilityBoost
                ))
                .toList();
    }

    private ScoredCandidate applyBoost(
            ScoredCandidate candidate,
            PlaceRecommendationCommerceSignalLoader.CommerceSignal signal,
            double benefitBoost,
            double availabilityBoost
    ) {
        double benefitScore = signal.activeBenefit() ? benefitBoost : 0d;
        double availabilityScore = signal.reservable() ? availabilityBoost : 0d;
        return new ScoredCandidate(
                candidate.place(),
                candidate.distanceMeters(),
                candidate.geoScore(),
                candidate.personalScore(),
                candidate.qualityScore(),
                candidate.engagementScore(),
                candidate.conversionScore(),
                candidate.explorationScore(),
                candidate.freshnessScore(),
                candidate.trustScore(),
                candidate.contextScore(),
                benefitScore,
                availabilityScore,
                candidate.dominantSignalType(),
                candidate.finalScore() + benefitScore + availabilityScore,
                candidate.candidateSource()
        );
    }
}
