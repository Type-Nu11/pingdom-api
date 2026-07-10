package com.typenull.pingdom.place.api.dto.place;

import com.typenull.pingdom.place.domain.place.TouristCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;

@Schema(description = "장소 상세 조회 응답")
public record PlaceDetailResponse(
        Long id,
        String name,
        @Schema(nullable = true)
        String englishName,
        String address,
        @Schema(nullable = true)
        String touristSummary,
        Set<TouristCategory> touristCategories,
        Double latitude,
        Double longitude,
        String registrant
) {
}
