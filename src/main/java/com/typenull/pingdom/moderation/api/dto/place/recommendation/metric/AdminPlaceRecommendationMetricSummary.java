package com.typenull.pingdom.moderation.api.dto.place.recommendation.metric;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 검색 대상 장소의 합계 건수와 합계에서 다시 계산한 소수 비율.
 * 비교 응답의 delta에 쓰일 때는 각 필드가 target - baseline이므로 음수가 될 수 있음.
 */
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
