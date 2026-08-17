package com.typenull.pingdom.moderation.api.dto.place.query;

import com.typenull.pingdom.place.domain.place.category.PlaceCategoryPolicy;
import com.typenull.pingdom.place.domain.place.category.TouristCategory;
import com.typenull.pingdom.place.domain.place.discovery.PlaceDiscoveryStatus;
import com.typenull.pingdom.place.domain.place.geocoding.GeocodingSource;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingStatus;
import com.typenull.pingdom.place.domain.place.statistics.PlaceGrowthSnapshot;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.Set;

public record AdminMapPlaceItem(
        Long id,
        String name,
        String address,
        @Schema(nullable = true)
        String roadAddress,
        @Schema(nullable = true)
        String jibunAddress,
        @Schema(nullable = true)
        String postalCode,
        GeocodingSource geocodingSource,
        PlaceOperatingStatus operatingStatus,
        @Schema(nullable = true)
        LocalDateTime operatingStatusCheckedAt,
        @Schema(
                description = "탐색 노출 상태. VISIBLE은 공개 탐색·자동완성·북마크 목록·추천 후보에 노출되고, HIDDEN은 제외됩니다.",
                example = "VISIBLE"
        )
        PlaceDiscoveryStatus discoveryStatus,
        @Schema(
                description = "일반 장소 카테고리. touristCategories와 별도 기준이며 미분류 장소는 null입니다.",
                nullable = true,
                example = "OTHER",
                allowableValues = {
                        PlaceCategoryPolicy.CAFE,
                        PlaceCategoryPolicy.RESTAURANT,
                        PlaceCategoryPolicy.TOURISM,
                        PlaceCategoryPolicy.SCENERY,
                        PlaceCategoryPolicy.CULTURE,
                        PlaceCategoryPolicy.SHOPPING,
                        PlaceCategoryPolicy.ACCOMMODATION,
                        PlaceCategoryPolicy.EXPERIENCE
                }
        )
        String category,
        @Schema(
                description = "화면 표시용 일반 장소 카테고리명. category가 없으면 미분류입니다.",
                example = "OTHER",
                allowableValues = {
                        PlaceCategoryPolicy.CAFE,
                        PlaceCategoryPolicy.RESTAURANT,
                        PlaceCategoryPolicy.TOURISM,
                        PlaceCategoryPolicy.SCENERY,
                        PlaceCategoryPolicy.CULTURE,
                        PlaceCategoryPolicy.SHOPPING,
                        PlaceCategoryPolicy.ACCOMMODATION,
                        PlaceCategoryPolicy.EXPERIENCE,
                        "미분류"
                }
        )
        String categoryName,
        @Schema(nullable = true)
        String englishName,
        @Schema(nullable = true)
        String touristSummary,
        @Schema(description = "외국인 관광 관심사 분류. 일반 장소 category 필터와는 별도 기준입니다.")
        Set<TouristCategory> touristCategories,
        Double latitude,
        Double longitude,
        Long userId,
        String registrant,
        PlaceGrowthSnapshot placeGrowth
) {
}
