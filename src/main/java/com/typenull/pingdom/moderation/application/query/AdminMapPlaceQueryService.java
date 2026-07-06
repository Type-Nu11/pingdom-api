package com.typenull.pingdom.moderation.application.query;

import com.typenull.pingdom.moderation.api.dto.place.duplicate.AdminMapPlaceDuplicateDetailResponse;
import com.typenull.pingdom.moderation.api.dto.place.duplicate.AdminMapPlaceDuplicateResponse;
import com.typenull.pingdom.moderation.api.dto.place.query.AdminMapPlaceDetailResponse;
import com.typenull.pingdom.moderation.api.dto.place.query.AdminMapPlaceResponse;
import com.typenull.pingdom.moderation.api.dto.place.recommendation.AdminPlaceRecommendationMetricsCompareResponse;
import com.typenull.pingdom.moderation.api.dto.place.recommendation.AdminPlaceRecommendationMetricsResponse;
import com.typenull.pingdom.moderation.domain.AdminPlaceSortParam;
import com.typenull.pingdom.moderation.domain.RecommendationMetricSortBy;
import com.typenull.pingdom.moderation.domain.SortParam;

public interface AdminMapPlaceQueryService {

    AdminMapPlaceResponse listPlaces(int page, int limit, AdminPlaceSortParam sortParam, String keyword);

    AdminMapPlaceDetailResponse getPlace(Long placeId, SortParam sortParam, String keyword);

    AdminMapPlaceDuplicateResponse listDuplicatePlaces();

    AdminMapPlaceDuplicateDetailResponse getDuplicatePlace(Long placeId);

    AdminPlaceRecommendationMetricsResponse listRecommendationMetrics(
            int page,
            int limit,
            RecommendationMetricSortBy sortBy,
            String keyword,
            String recommendationVersion,
            Integer days
    );

    AdminPlaceRecommendationMetricsCompareResponse compareRecommendationMetrics(
            String baselineVersion,
            String targetVersion,
            String keyword,
            Integer days
    );
}
