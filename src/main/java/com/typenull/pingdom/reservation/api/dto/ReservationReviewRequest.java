package com.typenull.pingdom.reservation.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReservationReviewRequest(@NotBlank @Size(max = 500) String reason) {}
