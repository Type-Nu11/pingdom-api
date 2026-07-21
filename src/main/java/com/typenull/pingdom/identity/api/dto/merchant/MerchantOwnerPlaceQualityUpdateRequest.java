package com.typenull.pingdom.identity.api.dto.merchant;

import com.typenull.pingdom.identity.domain.merchant.MerchantOperationalQualityStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record MerchantOwnerPlaceQualityUpdateRequest(
        @NotNull MerchantOperationalQualityStatus status,
        @NotNull @Min(0) @Max(100) Integer reservationResponseRate,
        @NotNull @Min(0) @Max(100) Integer reservationCancellationRate,
        @NotNull @Min(0) @Max(100) Integer noShowRate,
        LocalDateTime evaluatedAt,
        @Size(max = 500) String reason
) {
}
