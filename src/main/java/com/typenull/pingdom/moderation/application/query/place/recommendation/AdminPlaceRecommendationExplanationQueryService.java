package com.typenull.pingdom.moderation.application.query.place.recommendation;

import com.typenull.pingdom.moderation.api.dto.place.recommendation.explanation.AdminPlaceRecommendationExplanationItem;
import com.typenull.pingdom.moderation.api.dto.place.recommendation.explanation.AdminPlaceRecommendationExplanationResponse;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.recommendation.feature.PlaceRecommendationFeatureLog;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationFeatureLogRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminPlaceRecommendationExplanationQueryService {

    private final PlaceRecommendationFeatureLogRepository placeRecommendationFeatureLogRepository;
    private final MapPlaceRepository mapPlaceRepository;

    public AdminPlaceRecommendationExplanationResponse getExplanation(String requestId) {
        List<PlaceRecommendationFeatureLog> logs =
                placeRecommendationFeatureLogRepository.findByRequestIdOrderByRankingAsc(requestId);
        if (logs.isEmpty()) {
            throw new AdminException(AdminErrorCode.RECOMMENDATION_EXPLANATION_NOT_FOUND);
        }

        Map<Long, String> placeNames = loadPlaceNames(logs);
        List<AdminPlaceRecommendationExplanationItem> items = logs.stream()
                .map(log -> new AdminPlaceRecommendationExplanationItem(
                        log.getPlaceId(),
                        placeNames.getOrDefault(log.getPlaceId(), "알 수 없는 장소"),
                        log.getUserId(),
                        log.getRecommendationVersion(),
                        log.getRecommendationStage(),
                        log.getRanking(),
                        log.getCandidateSource(),
                        log.getDistanceMeters(),
                        log.getGeoScore(),
                        log.getPersonalScore(),
                        log.getQualityScore(),
                        log.getEngagementScore(),
                        log.getConversionScore(),
                        log.getExplorationScore(),
                        log.getFreshnessScore(),
                        log.getTrustScore(),
                        log.getFinalScore(),
                        log.getCreatedAt()
                ))
                .toList();
        return new AdminPlaceRecommendationExplanationResponse(requestId, items);
    }

    private Map<Long, String> loadPlaceNames(List<PlaceRecommendationFeatureLog> logs) {
        Map<Long, String> placeNames = new HashMap<>();
        List<Long> placeIds = logs.stream()
                .map(PlaceRecommendationFeatureLog::getPlaceId)
                .filter(placeId -> placeId != null)
                .distinct()
                .toList();
        for (MapPlace place : mapPlaceRepository.findAllById(placeIds)) {
            placeNames.put(place.getId(), place.getName());
        }
        return placeNames;
    }
}
