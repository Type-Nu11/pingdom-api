package com.typenull.pingdom.place.api.dto.recommendation;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "장소 추천 조회 응답")
public record PlaceRecommendationResponse(
        @Schema(description = "추천 장소 목록")
        List<PlaceRecommendationItem> places,
        @Schema(description = "추천 알고리즘 버전", example = "place-rec-v1")
        String recommendationVersion,
        @Schema(description = "추천 요청 식별자", example = "9f7263d5-65f1-4834-9ca3-86ad2fc4e7d0")
        String recommendationRequestId,
        @Schema(description = "요청 제한 수", example = "10")
        int limit,
        @Schema(description = "요청 반경(km)", example = "5.0")
        double requestedRadiusKm,
        @Schema(description = "실제 적용 반경(km)", example = "10.0")
        double appliedRadiusKm,
        @Schema(description = "추천 결과 수", example = "4")
        int recommendedCount
) {
    public static PlaceRecommendationResponse of(
            List<PlaceRecommendationItem> places,
            String recommendationVersion,
            String recommendationRequestId,
            int limit,
            double requestedRadiusKm,
            double appliedRadiusKm
    ) {
        return new PlaceRecommendationResponse(
                places,
                recommendationVersion,
                recommendationRequestId,
                limit,
                requestedRadiusKm,
                appliedRadiusKm,
                places.size()
        );
    }
}
