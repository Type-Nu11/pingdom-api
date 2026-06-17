package com.typenull.pingdom.place.api.dto.recommendation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "추천 장소 클릭 기록 요청")
public record PlaceRecommendationClickRequest(
        @NotNull(message = "placeId는 필수입니다.")
        @Schema(description = "클릭한 추천 장소 ID", example = "17")
        Long placeId,

        @NotBlank(message = "recommendationVersion은 필수입니다.")
        @Schema(description = "클릭한 추천 응답의 버전", example = "place-rec-v1")
        String recommendationVersion,

        @Schema(description = "추천 응답 requestId", example = "9f7263d5-65f1-4834-9ca3-86ad2fc4e7d0")
        String requestId
) {
}
