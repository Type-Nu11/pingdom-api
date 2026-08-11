package com.typenull.pingdom.place.api.dto.place.card;

import com.typenull.pingdom.place.domain.place.category.TouristCategory;
import com.typenull.pingdom.place.domain.place.geocoding.GeocodingSource;
import com.typenull.pingdom.place.domain.place.information.PlaceInformationSourceType;
import com.typenull.pingdom.place.domain.place.information.PlaceInformationVerificationStatus;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.Set;

@Schema(description = "관광객용 장소 카드 조회 응답")
public record TouristPlaceCardResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String name,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED)
        String englishName,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED)
        String imageUrl,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String address,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED)
        String roadAddress,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        GeocodingSource geocodingSource,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        PlaceOperatingStatus operatingStatus,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        boolean currentlyOperating,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDateTime currentlyOperatingCheckedAt,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED)
        String category,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED)
        String touristSummary,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Set<TouristCategory> touristCategories,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        PlaceInformationSourceType primaryInformationSource,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        PlaceInformationVerificationStatus informationVerificationStatus,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDateTime informationVerifiedAt,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDateTime informationEvidenceUpdatedAt,
        @Schema(description = "검증 완료된 근거 수", example = "3", requiredMode = Schema.RequiredMode.REQUIRED)
        int verifiedEvidenceCount,
        @Schema(description = "가장 최근 검증이 완료된 시각", nullable = true, requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDateTime lastVerifiedAt,
        @Schema(description = "가장 최근 검증 근거의 출처", nullable = true, requiredMode = Schema.RequiredMode.REQUIRED)
        PlaceInformationSourceType lastVerifiedSourceType,
        @Schema(minimum = "-90.0", maximum = "90.0", requiredMode = Schema.RequiredMode.REQUIRED)
        Double latitude,
        @Schema(minimum = "-180.0", maximum = "180.0", requiredMode = Schema.RequiredMode.REQUIRED)
        Double longitude
) {
}
