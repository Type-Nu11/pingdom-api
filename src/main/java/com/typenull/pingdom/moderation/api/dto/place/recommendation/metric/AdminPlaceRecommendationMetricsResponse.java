package com.typenull.pingdom.moderation.api.dto.place.recommendation.metric;

import com.typenull.pingdom.moderation.domain.RecommendationMetricSortBy;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "관리자 추천 성과 목록 응답")
public record AdminPlaceRecommendationMetricsResponse(
        List<AdminPlaceRecommendationMetricItem> metrics,
        RecommendationMetricSortBy sortBy,
        String recommendationVersion,
        Integer days,
        int page,
        int limit,
        long totalCount,
        long totalPages,
        boolean hasNext
) {
    public static AdminPlaceRecommendationMetricsResponse of(
            List<AdminPlaceRecommendationMetricItem> metrics,
            RecommendationMetricSortBy sortBy,
            String recommendationVersion,
            Integer days,
            int page,
            int limit,
            long totalCount,
            long totalPages
    ) {
        return new AdminPlaceRecommendationMetricsResponse(
                metrics,
                sortBy,
                recommendationVersion,
                days,
                page,
                limit,
                totalCount,
                totalPages,
                page < totalPages
        );
    }
}
