package com.typenull.pingdom.place.api.dto.recommendation;

import com.typenull.pingdom.place.domain.recommendation.candidate.PlaceRecommendationCandidateSource;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "추천 설명 항목")
public record PlaceRecommendationExplanationItem(
        @Schema(description = "장소 ID", example = "1")
        Long placeId,
        @Schema(description = "장소명", example = "진주성")
        String placeName,
        @Schema(description = "노출 순위", example = "1")
        int ranking,
        @Schema(description = "후보 소스", example = "PERSONALIZED")
        PlaceRecommendationCandidateSource source,
        @Schema(description = "거리(m)", example = "128")
        long distanceMeters,
        @Schema(description = "geo score", example = "0.91")
        double geoScore,
        @Schema(description = "personal score", example = "0.42")
        double personalScore,
        @Schema(description = "quality score", example = "0.37")
        double qualityScore,
        @Schema(description = "engagement score", example = "0.25")
        double engagementScore,
        @Schema(description = "conversion score", example = "0.14")
        double conversionScore,
        @Schema(description = "exploration score", example = "0.08")
        double explorationScore,
        @Schema(description = "freshness score", example = "0.19")
        double freshnessScore,
        @Schema(description = "검증 제보자 신뢰도 기반 score", example = "0.82")
        double trustScore,
        @Schema(description = "최종 score", example = "0.74")
        double finalScore
) {
}
