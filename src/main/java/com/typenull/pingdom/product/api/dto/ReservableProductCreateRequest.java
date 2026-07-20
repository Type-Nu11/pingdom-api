package com.typenull.pingdom.product.api.dto;

import com.typenull.pingdom.availability.domain.AvailabilityProductType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReservableProductCreateRequest(
        @NotNull Long placeId,
        @NotNull AvailabilityProductType productType,
        @NotBlank @Size(max = 100) String name
) {}
