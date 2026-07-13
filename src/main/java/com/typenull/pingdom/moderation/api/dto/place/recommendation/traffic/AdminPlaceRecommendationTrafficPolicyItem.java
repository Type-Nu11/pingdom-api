package com.typenull.pingdom.moderation.api.dto.place.recommendation.traffic;

import io.swagger.v3.oas.annotations.media.Schema;

public record AdminPlaceRecommendationTrafficPolicyItem(
        @Schema(description = "추천 버전", example = "place-rec-v1")
        String recommendationVersion,

        @Schema(description = "추천 단계", example = "STABLE")
        String stage,

        @Schema(description = "트래픽 비율", example = "70")
        int trafficPercentage,

        @Schema(description = "활성화 여부", example = "true")
        boolean enabled,

        @Schema(description = "비활성화 시 fallback 추천 버전", example = "place-rec-v1", nullable = true)
        String fallbackVersion
) {
}
