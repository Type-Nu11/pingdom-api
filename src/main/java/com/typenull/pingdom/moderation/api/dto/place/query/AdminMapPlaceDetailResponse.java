package com.typenull.pingdom.moderation.api.dto.place.query;

import com.typenull.pingdom.moderation.domain.SortParam;
import com.typenull.pingdom.place.domain.place.category.PlaceCategoryPolicy;
import com.typenull.pingdom.place.domain.place.discovery.PlaceDiscoveryStatus;
import com.typenull.pingdom.place.domain.place.geocoding.GeocodingSource;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingStatus;
import com.typenull.pingdom.place.domain.place.category.TouristCategory;
import com.typenull.pingdom.place.api.dto.place.operating.PlaceOperatingExceptionResponse;
import com.typenull.pingdom.place.api.dto.place.operating.PlaceRegularOperatingHourResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public record AdminMapPlaceDetailResponse(
        Long id,
        String name,
        @Schema(
                description = "장소 목록·상세에서 공통으로 사용하는 canonical 대표 이미지 URL입니다. 대표 이미지가 없으면 null입니다.",
                nullable = true,
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String imageUrl,
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
        List<PlaceRegularOperatingHourResponse> regularHours,
        List<PlaceOperatingExceptionResponse> operatingExceptions,
        @Schema(
                description = "일반 장소 카테고리. touristCategories와 별도 기준이며 미분류 장소는 null입니다.",
                nullable = true,
                example = "OTHER",
                allowableValues = {
                        PlaceCategoryPolicy.RESTAURANT,
                        PlaceCategoryPolicy.MUSIC,
                        PlaceCategoryPolicy.POP_UP,
                        PlaceCategoryPolicy.FASHION,
                        PlaceCategoryPolicy.BEAUTY,
                        PlaceCategoryPolicy.EXHIBITION,
                        PlaceCategoryPolicy.CAFE,
                        PlaceCategoryPolicy.CULTURAL_HERITAGE,
                        PlaceCategoryPolicy.OTHER
                }
        )
        String category,
        @Schema(
                description = "화면 표시용 일반 장소 카테고리명. category가 없으면 미분류입니다.",
                example = "기타",
                allowableValues = {
                        "음식점",
                        "음악",
                        "팝업",
                        "패션",
                        "뷰티",
                        "전시",
                        "카페",
                        "문화재",
                        "기타",
                        "미분류"
                }
        )
        String categoryName,
        @Schema(nullable = true)
        String englishName,
        @Schema(nullable = true)
        String touristSummary,
        @Schema(description = "외국인 관광 관심사 분류. 일반 장소 category와는 별도 기준입니다.")
        Set<TouristCategory> touristCategories,
        Double latitude,
        Double longitude,
        Long userId,
        String username,
        SortParam sortParam,
        int postCount,
        @Schema(
                description = "장소 성장 레벨. 10 이상이면 관리자 지도에서 불꽃 마커로 표시할 수 있습니다.",
                example = "5",
                minimum = "0",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        int level,
        AdminMapPlaceGrowthResponse placeGrowth,
        List<AdminMapPlaceImageItem> posts
) {
}
