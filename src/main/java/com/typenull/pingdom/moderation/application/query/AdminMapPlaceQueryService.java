package com.typenull.pingdom.moderation.application.query;

import com.typenull.pingdom.moderation.api.dto.place.AdminMapPlaceDetailResponse;
import com.typenull.pingdom.moderation.api.dto.place.AdminMapPlaceResponse;
import com.typenull.pingdom.moderation.api.dto.place.AdminPlaceRecommendationMetricsResponse;
import com.typenull.pingdom.moderation.domain.RecommendationMetricSortBy;
import com.typenull.pingdom.moderation.domain.SortParam;

public interface AdminMapPlaceQueryService {

    AdminMapPlaceResponse listPlaces(int page, int limit, SortParam sortParam, String keyword);

    AdminMapPlaceDetailResponse getPlace(Long placeId, SortParam sortParam, String keyword);

    AdminPlaceRecommendationMetricsResponse listRecommendationMetrics(
            int page,
            int limit,
            RecommendationMetricSortBy sortBy,
            String keyword,
            String recommendationVersion,
            Integer days
    );
}
