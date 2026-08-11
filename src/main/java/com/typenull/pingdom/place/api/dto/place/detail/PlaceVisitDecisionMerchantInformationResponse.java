package com.typenull.pingdom.place.api.dto.place.detail;

import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceInformation;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "관광객에게 공개하는 Merchant 장소 안내 정보")
public record PlaceVisitDecisionMerchantInformationResponse(
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) String description,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) String contactPhone,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) String websiteUrl,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) String reservationUrl,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        LocalDateTime updatedAt
) {

    public static PlaceVisitDecisionMerchantInformationResponse from(MerchantPlaceInformation information) {
        return new PlaceVisitDecisionMerchantInformationResponse(
                information.getDescription(),
                information.getContactPhone(),
                information.getWebsiteUrl(),
                information.getReservationUrl(),
                information.getUpdatedAt()
        );
    }
}
