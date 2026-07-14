package com.typenull.pingdom.moderation.api.dto.place.recommendation.metric;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 추천 버전 성과 비교 응답")
public record AdminPlaceRecommendationMetricsCompareResponse(
        @Schema(description = "기준 추천 버전", example = "place-rec-v1")
        String baselineVersion,
        @Schema(description = "비교 대상 추천 버전", example = "place-rec-v2")
        String targetVersion,
        @Schema(description = "최근 N일 기준 필터", example = "7")
        Integer days,
        @Schema(description = "장소 검색 키워드", example = "진주")
        String keyword,
        @Schema(description = "기준 버전 성과 요약")
        AdminPlaceRecommendationMetricSummary baseline,
        @Schema(description = "비교 대상 버전 성과 요약")
        AdminPlaceRecommendationMetricSummary target,
        @Schema(description = "대상 버전 - 기준 버전 차이")
        AdminPlaceRecommendationMetricSummary delta
) {
}
