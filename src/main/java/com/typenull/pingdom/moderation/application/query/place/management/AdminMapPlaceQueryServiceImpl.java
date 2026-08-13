package com.typenull.pingdom.moderation.application.query.place.management;

import com.typenull.pingdom.moderation.application.query.place.duplicate.AdminMapPlaceDuplicateQueryService;
import com.typenull.pingdom.moderation.application.query.place.lookup.AdminMapPlaceLookupQueryService;
import com.typenull.pingdom.moderation.application.query.place.recommendation.AdminPlaceRecommendationMetricQueryService;

import com.typenull.pingdom.moderation.api.dto.place.duplicate.AdminMapPlaceDuplicateDetailResponse;
import com.typenull.pingdom.moderation.api.dto.place.duplicate.AdminMapPlaceDuplicateResponse;
import com.typenull.pingdom.moderation.api.dto.place.query.AdminMapPlaceDetailResponse;
import com.typenull.pingdom.moderation.api.dto.place.query.AdminMapPlaceResponse;
import com.typenull.pingdom.moderation.api.dto.place.recommendation.metric.AdminPlaceRecommendationMetricsCompareResponse;
import com.typenull.pingdom.moderation.api.dto.place.recommendation.metric.AdminPlaceRecommendationMetricsResponse;
import com.typenull.pingdom.moderation.domain.AdminPlaceSortParam;
import com.typenull.pingdom.moderation.domain.RecommendationMetricSortBy;
import com.typenull.pingdom.moderation.domain.SortParam;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminMapPlaceQueryServiceImpl implements AdminMapPlaceQueryService {

    private final AdminMapPlaceLookupQueryService lookupQueryService;
    private final AdminMapPlaceDuplicateQueryService duplicateQueryService;
    private final AdminPlaceRecommendationMetricQueryService recommendationMetricQueryService;

    @Override
    public AdminMapPlaceResponse listPlaces(
            int page,
            int limit,
            AdminPlaceSortParam sortParam,
            String keyword,
            String category
    ) {
        return lookupQueryService.listPlaces(page, limit, sortParam, keyword, category);
    }

    @Override
    public AdminMapPlaceDetailResponse getPlace(Long placeId, SortParam sortParam, String keyword) {
        return lookupQueryService.getPlace(placeId, sortParam, keyword);
    }

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
