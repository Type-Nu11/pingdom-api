package com.typenull.pingdom.place.application.service.recommendation.explanation;

import com.typenull.pingdom.place.api.dto.recommendation.PlaceRecommendationExplanationItem;
import com.typenull.pingdom.place.api.dto.recommendation.PlaceRecommendationExplanationResponse;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.recommendation.feature.PlaceRecommendationFeatureLog;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationFeatureLogRepository;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaceRecommendationExplanationQueryService {

    private final PlaceRecommendationFeatureLogRepository placeRecommendationFeatureLogRepository;
    private final MapPlaceRepository mapPlaceRepository;

    public PlaceRecommendationExplanationResponse getExplanation(Long userId, String requestId) {
        List<PlaceRecommendationFeatureLog> logs = placeRecommendationFeatureLogRepository
                .findByRequestIdAndUserIdOrderByRankingAsc(requestId, userId);
        if (logs.isEmpty()) {
            throw new MapException(MapErrorCode.RECOMMENDATION_EXPLANATION_NOT_FOUND);
        }

        Map<Long, String> placeNames = loadPlaceNames(logs);
        List<PlaceRecommendationExplanationItem> items = logs.stream()
                .map(log -> new PlaceRecommendationExplanationItem(
                        log.getPlaceId(),
                        placeNames.getOrDefault(log.getPlaceId(), "알 수 없는 장소"),
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
                        log.getFinalScore()
                ))
                .toList();
        return new PlaceRecommendationExplanationResponse(requestId, items);
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
