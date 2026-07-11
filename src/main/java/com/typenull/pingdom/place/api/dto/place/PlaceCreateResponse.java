package com.typenull.pingdom.place.api.dto.place;

import com.typenull.pingdom.place.domain.place.TouristCategory;
import com.typenull.pingdom.place.domain.place.GeocodingSource;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;

@Schema(description = "장소 생성 응답")
public record PlaceCreateResponse(
        @Schema(description = "생성된 장소 ID", example = "1")
        Long id,
        @Schema(description = "장소 이름", example = "카페")
        String name,
        @Schema(description = "영문 장소 이름", example = "Jinju Castle", nullable = true)
        String englishName,
        @Schema(description = "주소", example = "서울특별시 ...")
        String address,
        @Schema(description = "도로명 주소", nullable = true)
        String roadAddress,
        @Schema(description = "지번 주소", nullable = true)
        String jibunAddress,
        @Schema(description = "우편번호", nullable = true)
        String postalCode,
        @Schema(description = "주소 및 좌표 생성 출처")
        GeocodingSource geocodingSource,
        @Schema(description = "관광객용 장소 요약", nullable = true)
        String touristSummary,
        @Schema(description = "관광 목적 카테고리")
        Set<TouristCategory> touristCategories,
        @Schema(description = "위도", example = "37.5665")
        Double latitude,
        @Schema(description = "경도", example = "126.9780")
        Double longitude
) {
}
