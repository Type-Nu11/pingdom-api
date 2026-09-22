package com.typenull.pingdom.place.application.service.recommendation.query;

import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationTrustScoreRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 후보별 승인 제보 신뢰도를 불러오고 조회된 null 점수를 중립값 0.5로, 수치를 0~1 범위로 보정.
 * 조회 행 자체가 없는 후보는 점수 계산 서비스가 동일한 중립값을 적용.
 */
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
