package com.typenull.pingdom.identity.api.dto.merchant;

import com.typenull.pingdom.identity.domain.merchant.MerchantPlaceInformation;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

@Schema(description = "Merchant 장소 정보 응답")
public record MerchantPlaceInformationResponse(
        Long placeId,
        @Schema(nullable = true) String description,
        @Schema(nullable = true) String contactPhone,
        @Schema(nullable = true) String websiteUrl,
        @Schema(nullable = true) String reservationUrl,
        @Schema(nullable = true) Long updatedByUserId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
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
