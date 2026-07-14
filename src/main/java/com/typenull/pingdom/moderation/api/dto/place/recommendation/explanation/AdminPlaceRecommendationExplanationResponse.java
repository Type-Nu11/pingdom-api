package com.typenull.pingdom.moderation.api.dto.place.recommendation.explanation;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "관리자 추천 설명 조회 응답")
public record AdminPlaceRecommendationExplanationResponse(
        @Schema(description = "추천 요청 식별자", example = "9f7263d5-65f1-4834-9ca3-86ad2fc4e7d0")
        String requestId,
        @Schema(description = "관리자 추천 설명 항목")
        List<AdminPlaceRecommendationExplanationItem> items
) {
}
