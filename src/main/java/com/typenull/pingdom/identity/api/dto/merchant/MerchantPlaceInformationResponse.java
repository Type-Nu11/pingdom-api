package com.typenull.pingdom.identity.api.dto.merchant;

import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceInformation;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "Merchant 장소 정보 응답")
public record MerchantPlaceInformationResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long placeId,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) String description,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) String contactPhone,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) String websiteUrl,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) String reservationUrl,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED) Long updatedByUserId,
        @Schema(format = "date-time", requiredMode = Schema.RequiredMode.REQUIRED) LocalDateTime createdAt,
        @Schema(format = "date-time", requiredMode = Schema.RequiredMode.REQUIRED) LocalDateTime updatedAt
) {

    public static MerchantPlaceInformationResponse from(MerchantPlaceInformation information) {
        return new MerchantPlaceInformationResponse(
                information.getPlaceId(),
                information.getDescription(),
                information.getContactPhone(),
                information.getWebsiteUrl(),
                information.getReservationUrl(),
                information.getUpdatedByUserId(),
                information.getCreatedAt(),
                information.getUpdatedAt()
        );
    }
}
