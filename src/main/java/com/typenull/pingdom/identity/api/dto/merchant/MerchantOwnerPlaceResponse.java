package com.typenull.pingdom.identity.api.dto.merchant;

import com.typenull.pingdom.identity.domain.merchant.MerchantOperationalQualityStatus;
import com.typenull.pingdom.identity.domain.merchant.MerchantOwnerPlace;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record MerchantOwnerPlaceResponse(
        Long placeId,
        Long merchantOwnerUserId,
        MerchantOperationalQualityStatus operationalQualityStatus,
        Integer reservationResponseRate,
        Integer reservationCancellationRate,
        Integer noShowRate,
        @Schema(nullable = true) LocalDateTime qualityEvaluatedAt,
        LocalDateTime createdAt
) {
    public static MerchantOwnerPlaceResponse from(MerchantOwnerPlace place) {
        return new MerchantOwnerPlaceResponse(
                place.getPlaceId(),
                place.getMerchantOwnerUserId(),
                place.getOperationalQualityStatus(),
                place.getReservationResponseRate(),
                place.getReservationCancellationRate(),
                place.getNoShowRate(),
                place.getQualityEvaluatedAt(),
                place.getCreatedAt()
        );
    }
}
