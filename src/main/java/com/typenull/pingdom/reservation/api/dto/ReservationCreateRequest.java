package com.typenull.pingdom.reservation.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReservationCreateRequest(
        @NotNull Long availabilityId,
        @NotBlank @Size(max = 100) String idempotencyKey,
        @Min(1) int quantity
) {
}
