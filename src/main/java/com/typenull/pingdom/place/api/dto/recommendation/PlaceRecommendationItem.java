package com.typenull.pingdom.place.api.dto.recommendation;

import com.typenull.pingdom.place.domain.place.PlaceGrowthSnapshot;
import com.typenull.pingdom.place.domain.place.GeocodingSource;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "장소 추천 항목")
public record PlaceRecommendationItem(
        @Schema(description = "장소 ID", example = "5")
        Long id,
        @Schema(description = "장소명", example = "진주성")
        String name,
        @Schema(description = "장소 주소", example = "경상남도 진주시 남강로 626")
        String address,
        @Schema(description = "도로명 주소", nullable = true)
        String roadAddress,
        @Schema(description = "지번 주소", nullable = true)
        String jibunAddress,
        @Schema(description = "우편번호", nullable = true)
        String postalCode,
        @Schema(description = "주소 및 좌표 생성 출처")
        GeocodingSource geocodingSource,
        @Schema(description = "장소 위도", example = "35.1894")
        Double latitude,
        @Schema(description = "장소 경도", example = "128.0789")
        Double longitude,
        @Schema(description = "현재 위치와의 거리(미터)", example = "215")
        long distanceMeters,
        @Schema(description = "추천 사유", example = "저장한 장소와 가까운 추천 장소입니다.")
        String reason,
        @Schema(description = "장소 성장 상태")
        PlaceGrowthSnapshot placeGrowth
) {
}
