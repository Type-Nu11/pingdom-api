package com.typenull.pingdom.moderation.api.dto.place.recommendation.explanation;

import com.typenull.pingdom.place.domain.recommendation.candidate.PlaceRecommendationCandidateSource;
import com.typenull.pingdom.place.support.PlaceRecommendationProperties.RecommendationStage;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "관리자 추천 설명 항목")
public record AdminPlaceRecommendationExplanationItem(
        @Schema(description = "장소 ID", example = "1")
        Long placeId,
        @Schema(description = "장소명", example = "진주성")
        String placeName,
        @Schema(description = "사용자 ID", example = "21")
        Long userId,
        @Schema(description = "추천 버전", example = "place-rec-v2")
        String recommendationVersion,
        @Schema(description = "추천 stage", example = "EXPERIMENT")
        RecommendationStage recommendationStage,
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
        @Schema(description = "K-컬처 관심사와 현재 여행 맥락 기반 score", example = "0.25")
        double contextScore,
        @Schema(description = "현재 이용 가능한 혜택 기반 boost", example = "0.05")
        double benefitScore,
        @Schema(description = "예약 가능 시간 기반 boost", example = "0.05")
        double availabilityScore,
        @Schema(description = "최종 score", example = "0.74")
        double finalScore,
        @Schema(description = "로그 생성 시각", example = "2026-06-25T16:00:00")
        LocalDateTime createdAt
) {
}
