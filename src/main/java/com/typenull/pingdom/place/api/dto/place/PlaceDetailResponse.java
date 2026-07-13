package com.typenull.pingdom.place.api.dto.place;

import com.typenull.pingdom.place.domain.place.TouristCategory;
import com.typenull.pingdom.place.domain.place.GeocodingSource;
import com.typenull.pingdom.place.domain.place.PlaceOperatingStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Schema(description = "장소 상세 조회 응답")
public record PlaceDetailResponse(
        Long id,
        String name,
        @Schema(nullable = true)
        String englishName,
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
        List<PlaceRegularOperatingHourResponse> regularHours,
        List<PlaceOperatingExceptionResponse> operatingExceptions,
        @Schema(nullable = true)
        String touristSummary,
        Set<TouristCategory> touristCategories,
        Double latitude,
        Double longitude,
        String registrant
) {
}
