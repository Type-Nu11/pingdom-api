package com.typenull.pingdom.moderation.api.dto.place.recommendation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record AdminPlaceRecommendationTrafficUpdateItem(
        @NotBlank(message = "recommendationVersion은 필수입니다.")
        @Schema(description = "추천 버전", example = "place-rec-v2")
        String recommendationVersion,

        @Min(value = 0, message = "trafficPercentage는 0 이상이어야 합니다.")
        @Max(value = 100, message = "trafficPercentage는 100 이하여야 합니다.")
        @Schema(description = "트래픽 비율", example = "30")
        Integer trafficPercentage
) {
}
