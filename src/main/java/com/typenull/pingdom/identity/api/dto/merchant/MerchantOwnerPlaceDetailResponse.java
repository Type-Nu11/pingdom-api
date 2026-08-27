package com.typenull.pingdom.identity.api.dto.merchant;

import com.typenull.pingdom.place.api.dto.place.operating.PlaceOperatingExceptionResponse;
import com.typenull.pingdom.place.api.dto.place.operating.PlaceRegularOperatingHourResponse;
import com.typenull.pingdom.place.domain.place.category.TouristCategory;
import com.typenull.pingdom.place.domain.place.geocoding.GeocodingSource;
import com.typenull.pingdom.place.domain.place.information.PlaceInformationSourceType;
import com.typenull.pingdom.place.domain.place.information.PlaceInformationVerificationStatus;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Schema(description = "Merchant Owner 장소 상세 응답")
public record MerchantOwnerPlaceDetailResponse(
        Long id,
        String name,
        String englishName,
        String category,
        String address,
        String roadAddress,
        String jibunAddress,
        String postalCode,
        GeocodingSource geocodingSource,
        Double latitude,
        Double longitude,
        String imageUrl,
        PlaceOperatingStatus operatingStatus,
        LocalDateTime operatingStatusCheckedAt,
        List<PlaceRegularOperatingHourResponse> regularHours,
        List<PlaceOperatingExceptionResponse> operatingExceptions,
        String description,
        String touristSummary,
        Set<TouristCategory> touristCategories,
        PlaceInformationSourceType primaryInformationSource,
        PlaceInformationVerificationStatus informationVerificationStatus,
        LocalDateTime informationVerifiedAt,
        LocalDateTime informationEvidenceUpdatedAt
) {
}
