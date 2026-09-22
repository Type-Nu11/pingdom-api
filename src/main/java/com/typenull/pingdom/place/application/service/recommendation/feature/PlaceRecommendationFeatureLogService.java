package com.typenull.pingdom.place.application.service.recommendation.feature;

import com.typenull.pingdom.place.domain.recommendation.feature.PlaceRecommendationFeatureLog;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationFeatureLogRepository;
import com.typenull.pingdom.place.support.PlaceRecommendationProperties.RecommendationStage;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 실제로 표시한 후보의 거리·신호별 점수·최종 순위를 저장하고 클릭 전환과 연결할 로그 ID를 찾습니다.
 * 연결할 requestId나 로그가 없으면 null을 반환하므로 전환 기록이 특성 로그 존재에 종속되지는 않습니다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaceRecommendationFeatureLogService {

    private final PlaceRecommendationFeatureLogRepository placeRecommendationFeatureLogRepository;

    /**
     * 실제로 표시한 후보의 순위·신호별 점수를 요청·사용자·정책 버전과 함께 일괄 저장합니다.
     * 빈 목록은 건너뛰며 null 목록은 허용하지 않습니다. 저장 실패는 전파되어 참여 중인 추천 요청 트랜잭션도 실패할 수 있습니다.
     */
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

    /**
     * 요청·사용자·장소·버전이 일치하는 특성 로그 중 가장 작은 ID를 전환 귀속 대상으로 반환합니다.
     * 요청 ID가 공백이거나 일치 로그가 없으면 null을 반환하여 특성 로그 없는 전환도 허용합니다.
     */
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
