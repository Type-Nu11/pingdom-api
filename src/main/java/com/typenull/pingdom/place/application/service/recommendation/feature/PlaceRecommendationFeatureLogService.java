package com.typenull.pingdom.place.application.service.recommendation.feature;

import com.typenull.pingdom.place.domain.recommendation.feature.PlaceRecommendationFeatureLog;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationFeatureLogRepository;
import com.typenull.pingdom.place.support.PlaceRecommendationProperties.RecommendationStage;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaceRecommendationFeatureLogService {

    private final PlaceRecommendationFeatureLogRepository placeRecommendationFeatureLogRepository;

    @Transactional
    public void recordShownCandidates(
            String requestId,
            Long userId,
            String recommendationVersion,
            RecommendationStage recommendationStage,
            List<PlaceRecommendationFeatureRecord> records
    ) {
        if (records.isEmpty()) {
            return;
        }

        List<PlaceRecommendationFeatureLog> logs = records.stream()
                .map(record -> PlaceRecommendationFeatureLog.builder()
                        .requestId(requestId)
                        .userId(userId)
                        .placeId(record.placeId())
                        .recommendationVersion(recommendationVersion)
                        .recommendationStage(recommendationStage)
                        .candidateSource(record.candidateSource())
                        .ranking(record.ranking())
                        .distanceMeters(record.distanceMeters())
                        .geoScore(record.geoScore())
                        .personalScore(record.personalScore())
                        .qualityScore(record.qualityScore())
                        .engagementScore(record.engagementScore())
                        .conversionScore(record.conversionScore())
                        .explorationScore(record.explorationScore())
                        .freshnessScore(record.freshnessScore())
                        .trustScore(record.trustScore())
                        .contextScore(record.contextScore())
                        .benefitScore(record.benefitScore())
                        .availabilityScore(record.availabilityScore())
                        .boostScore(record.boostScore())
                        .finalScore(record.finalScore())
                        .build())
                .toList();

        placeRecommendationFeatureLogRepository.saveAll(logs);
    }

    public Long findFeatureLogId(String requestId, Long userId, Long placeId, String recommendationVersion) {
        if (!StringUtils.hasText(requestId)) {
            return null;
        }

        return placeRecommendationFeatureLogRepository
                .findFirstByRequestIdAndUserIdAndPlaceIdAndRecommendationVersionOrderByIdAsc(
                        requestId,
                        userId,
                        placeId,
                        recommendationVersion
                )
                .map(PlaceRecommendationFeatureLog::getId)
                .orElse(null);
    }
}
