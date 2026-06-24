package com.typenull.pingdom.moderation.api.dto.place.recommendation;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "관리자 추천 성과 요약")
public record AdminPlaceRecommendationMetricSummary(
        @Schema(description = "추천 버전", example = "place-rec-v1")
        String recommendationVersion,
        @Schema(description = "추천 노출 수", example = "120")
        long exposureCount,
        @Schema(description = "추천 클릭 수", example = "18")
        long clickCount,
        @Schema(description = "원본 CTR", example = "0.15")
        double rawCtr,
        @Schema(description = "smoothed CTR", example = "0.13")
        double smoothedCtr,
        @Schema(description = "북마크 전환 수", example = "4")
        long bookmarkConversionCount,
        @Schema(description = "좋아요 전환 수", example = "6")
        long likeConversionCount,
        @Schema(description = "북마크 전환율", example = "0.03")
        double bookmarkConversionRate,
        @Schema(description = "좋아요 전환율", example = "0.05")
        double likeConversionRate,
        @Schema(description = "전체 전환율", example = "0.08")
        double totalConversionRate
) {
}
