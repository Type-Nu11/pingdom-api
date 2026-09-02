package com.typenull.pingdom.menu.api.dto;

import com.typenull.pingdom.menu.domain.MenuCurrency;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record PlaceMenuUpdateRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 500) String description,
        @NotNull @Positive @Max(1_000_000_000L) Long priceAmount,
        @NotNull MenuCurrency currency,
        @Schema(nullable = true, description = "기존 공개 미디어 URL. 없으면 null") @Size(max = 500) String imageUrl
) {}
