package com.typenull.pingdom.place.api.dto.place.detail;

import com.typenull.pingdom.identity.api.dto.merchant.MerchantOwnerPublicResponse;
import com.typenull.pingdom.place.api.dto.place.operating.PlaceOperatingExceptionResponse;
import com.typenull.pingdom.place.api.dto.place.operating.PlaceRegularOperatingHourResponse;
import com.typenull.pingdom.place.api.dto.place.operating.notice.PlaceOperatingNoticeResponse;

import com.typenull.pingdom.place.domain.place.category.TouristCategory;
import com.typenull.pingdom.place.domain.place.geocoding.GeocodingSource;
import com.typenull.pingdom.place.domain.place.information.PlaceInformationSourceType;
import com.typenull.pingdom.place.domain.place.information.PlaceInformationVerificationStatus;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Schema(description = "장소 상세 조회 응답")
public record PlaceDetailResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        Long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String name,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED)
        String englishName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String address,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED)
        String roadAddress,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED)
        String jibunAddress,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED)
        String postalCode,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        GeocodingSource geocodingSource,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        PlaceOperatingStatus operatingStatus,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDateTime operatingStatusCheckedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        boolean currentlyOperating,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDateTime currentlyOperatingCheckedAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<PlaceRegularOperatingHourResponse> regularHours,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<PlaceOperatingExceptionResponse> operatingExceptions,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        List<PlaceOperatingNoticeResponse> activeOperatingNotices,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED)
        String description,
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
        Double longitude,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        String registrant,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED)
        MerchantOwnerPublicResponse merchantOwner
) {
}
