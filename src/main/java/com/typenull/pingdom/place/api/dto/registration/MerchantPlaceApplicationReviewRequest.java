package com.typenull.pingdom.place.api.dto.registration;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record MerchantPlaceApplicationReviewRequest(
        @NotNull @PositiveOrZero Long reviewedVersion,
        @Size(max = 500) String reason
) {
}
