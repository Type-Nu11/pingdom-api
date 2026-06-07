package com.typenull.pingdom.place.api.dto.recommendation;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "추천 장소 클릭 기록 응답")
public record PlaceRecommendationClickResponse(
        @Schema(description = "클릭 기록이 저장된 장소 ID", example = "17")
        Long placeId,
        @Schema(description = "처리 결과 메시지", example = "추천 장소 클릭을 기록했습니다.")
        String message
) {
}
