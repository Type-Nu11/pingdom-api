package com.typenull.pingdom.moderation.api.dto.place.query;

import com.typenull.pingdom.moderation.domain.SortParam;
import com.typenull.pingdom.place.domain.place.PlaceGrowthSnapshot;
import com.typenull.pingdom.place.domain.place.GeocodingSource;
import com.typenull.pingdom.place.domain.place.PlaceOperatingStatus;
import com.typenull.pingdom.place.domain.place.TouristCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public record AdminMapPlaceDetailResponse(
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
        String category,
        String categoryName,
        @Schema(nullable = true)
        String englishName,
        @Schema(nullable = true)
        String touristSummary,
        Set<TouristCategory> touristCategories,
        Double latitude,
        Double longitude,
        Long userId,
        String username,
        SortParam sortParam,
        int postCount,
        PlaceGrowthSnapshot placeGrowth,
        List<AdminMapPlaceImageItem> posts
) {
}
