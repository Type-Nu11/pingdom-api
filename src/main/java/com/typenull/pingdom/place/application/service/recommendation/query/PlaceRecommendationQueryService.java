package com.typenull.pingdom.place.application.service.recommendation.query;

import com.typenull.pingdom.place.api.dto.recommendation.PlaceRecommendationResponse;

/**
 * 주변 추천을 조회하고 선택된 정책에 따라 특성 로그·노출 관측을 요청하는 진입점.
 * userId는 익명일 때 null이며 좌표는 도, radiusKm은 km 단위.
 */
public interface PlaceRecommendationQueryService {
    PlaceRecommendationResponse recommendAndRecordObservations(
            Long userId,
            double latitude,
            double longitude,
            int limit,
            double radiusKm,
            String requestedRecommendationVersion
    );
}
