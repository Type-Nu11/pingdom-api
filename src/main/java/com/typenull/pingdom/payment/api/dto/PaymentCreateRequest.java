package com.typenull.pingdom.payment.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PaymentCreateRequest(
        @NotNull Long reservationId,
        @NotBlank @Size(max = 30) String provider,
        @NotBlank @Size(max = 500) String paymentToken,
        @NotBlank @Size(max = 100) String idempotencyKey
) {}
