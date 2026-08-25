package com.typenull.pingdom.moderation.application.query.place.management;

import com.typenull.pingdom.moderation.api.dto.place.duplicate.AdminMapPlaceDuplicateDetailResponse;
import com.typenull.pingdom.moderation.api.dto.place.duplicate.AdminMapPlaceDuplicateResponse;
import com.typenull.pingdom.moderation.api.dto.place.recommendation.metric.AdminPlaceRecommendationMetricsCompareResponse;
import com.typenull.pingdom.moderation.api.dto.place.recommendation.metric.AdminPlaceRecommendationMetricsResponse;
import com.typenull.pingdom.moderation.domain.RecommendationMetricSortBy;
import com.typenull.pingdom.moderation.domain.SortParam;

public interface AdminMapPlaceQueryService {

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
