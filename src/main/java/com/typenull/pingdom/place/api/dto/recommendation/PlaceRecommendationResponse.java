package com.typenull.pingdom.place.api.dto.recommendation;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "장소 추천 조회 응답")
public record PlaceRecommendationResponse(
        @Schema(description = "추천 장소 목록")
        List<PlaceRecommendationItem> places,
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
            int limit,
            double requestedRadiusKm,
            double appliedRadiusKm
    ) {
        return new PlaceRecommendationResponse(
                places,
                limit,
                requestedRadiusKm,
                appliedRadiusKm,
                places.size()
        );
    }
}
