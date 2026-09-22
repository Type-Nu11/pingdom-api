package com.typenull.pingdom.place.application.service.recommendation.query;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 조회된 혜택·예약 가능 여부를 각각 정책 가점으로 바꾸어 최종 점수에 더합니다.
 * 이 단계는 후보 순서를 유지하며 최종 정렬과 후보 선택은 포트폴리오 단계가 맡습니다.
 */
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
                candidate.boostScore(),
                candidate.dominantSignalType(),
                candidate.finalScore() + benefitScore + availabilityScore,
                candidate.candidateSource()
        );
    }
}
