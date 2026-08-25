package com.typenull.pingdom.moderation.application.query.place.management;

import com.typenull.pingdom.moderation.application.query.place.duplicate.AdminMapPlaceDuplicateQueryService;
import com.typenull.pingdom.moderation.application.query.place.recommendation.AdminPlaceRecommendationMetricQueryService;

import com.typenull.pingdom.moderation.api.dto.place.duplicate.AdminMapPlaceDuplicateDetailResponse;
import com.typenull.pingdom.moderation.api.dto.place.duplicate.AdminMapPlaceDuplicateResponse;
import com.typenull.pingdom.moderation.api.dto.place.recommendation.metric.AdminPlaceRecommendationMetricsCompareResponse;
import com.typenull.pingdom.moderation.api.dto.place.recommendation.metric.AdminPlaceRecommendationMetricsResponse;
import com.typenull.pingdom.moderation.domain.RecommendationMetricSortBy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
/** 관리자 장소 목록·상세·중복·추천 지표 조회를 읽기 모델로 조합합니다. */
public class AdminMapPlaceQueryServiceImpl implements AdminMapPlaceQueryService {

    private final AdminMapPlaceDuplicateQueryService duplicateQueryService;
    private final AdminPlaceRecommendationMetricQueryService recommendationMetricQueryService;

    @Override
    public AdminMapPlaceDuplicateResponse listDuplicatePlaces() {
        return duplicateQueryService.listDuplicatePlaces();
    }

    @Override
    public AdminMapPlaceDuplicateDetailResponse getDuplicatePlace(Long placeId) {
        return duplicateQueryService.getDuplicatePlace(placeId);
    }

    @Override
    public AdminPlaceRecommendationMetricsResponse listRecommendationMetrics(
            int page,
            int limit,
            RecommendationMetricSortBy sortBy,
            String keyword,
            String recommendationVersion,
            Integer days
    ) {
        return recommendationMetricQueryService.listRecommendationMetrics(
                page,
                limit,
                sortBy,
                keyword,
                recommendationVersion,
                days
        );
    }

    @Override
    public AdminPlaceRecommendationMetricsCompareResponse compareRecommendationMetrics(
            String baselineVersion,
            String targetVersion,
            String keyword,
            Integer days
    ) {
        return recommendationMetricQueryService.compareRecommendationMetrics(
                baselineVersion,
                targetVersion,
                keyword,
                days
        );
    }
}
