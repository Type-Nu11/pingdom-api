package com.typenull.pingdom.reservation.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ReservationCreateRequest(
        @NotNull Long availabilityId,
        @NotBlank @Size(max = 100) String idempotencyKey,
        @Min(1) int quantity,
        @NotBlank @Size(max = 100) String bookerName,
        @NotBlank @Size(max = 30) @Pattern(regexp = "^[0-9+()\\- ]+$") String bookerPhone,
        @Size(max = 500) String requestNote
) {
    public ReservationCreateRequest(Long availabilityId, String idempotencyKey, int quantity) {
        this(availabilityId, idempotencyKey, quantity, "예약자", "00000000", null);
    }
}
