package com.typenull.pingdom.availability.api.dto;

import com.typenull.pingdom.availability.domain.ReservationTerms;
import jakarta.validation.constraints.*;

public record ReservationTermsRequest(
        @NotNull @Min(0) Long unitAmountMinor,
        @NotNull @Min(0) Long additionalAmountMinor,
        @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency,
        @NotBlank @Size(max = 64) String timezone,
        @NotNull Boolean cancellable,
        @Min(0) Integer cancellationCutoffMinutes
) {
    public ReservationTerms toTerms() {
        return new ReservationTerms(unitAmountMinor, additionalAmountMinor, currency, timezone,
                cancellable, cancellationCutoffMinutes);
    }
}
