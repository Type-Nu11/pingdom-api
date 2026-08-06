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
        Long id,
        String name,
        @Schema(nullable = true)
        String englishName,
        @Schema(nullable = true)
        String imageUrl,
        String address,
        @Schema(nullable = true)
        String roadAddress,
        GeocodingSource geocodingSource,
        PlaceOperatingStatus operatingStatus,
        boolean currentlyOperating,
        LocalDateTime currentlyOperatingCheckedAt,
        @Schema(nullable = true)
        String category,
        @Schema(nullable = true)
        String touristSummary,
        Set<TouristCategory> touristCategories,
        PlaceInformationSourceType primaryInformationSource,
        PlaceInformationVerificationStatus informationVerificationStatus,
        @Schema(nullable = true)
        LocalDateTime informationVerifiedAt,
        @Schema(nullable = true)
        LocalDateTime informationEvidenceUpdatedAt,
        @Schema(description = "검증 완료된 근거 수", example = "3")
        int verifiedEvidenceCount,
        @Schema(description = "가장 최근 검증이 완료된 시각", nullable = true)
        LocalDateTime lastVerifiedAt,
        @Schema(description = "가장 최근 검증 근거의 출처", nullable = true)
        PlaceInformationSourceType lastVerifiedSourceType,
        Double latitude,
        Double longitude
) {
}
