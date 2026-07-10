package com.typenull.pingdom.moderation.api.dto.place.query;

import com.typenull.pingdom.moderation.domain.SortParam;
import com.typenull.pingdom.place.domain.place.PlaceGrowthSnapshot;
import com.typenull.pingdom.place.domain.place.TouristCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Set;

public record AdminMapPlaceDetailResponse(
        Long id,
        String name,
        String address,
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
