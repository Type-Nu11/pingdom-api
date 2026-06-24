package com.typenull.pingdom.moderation.api.dto.place.recommendation;

import io.swagger.v3.oas.annotations.media.Schema;

public record AdminPlaceRecommendationTrafficPolicyItem(
        @Schema(description = "추천 버전", example = "place-rec-v1")
        String recommendationVersion,

        @Schema(description = "추천 단계", example = "STABLE")
        String stage,

        @Schema(description = "트래픽 비율", example = "70")
        int trafficPercentage
) {
}
