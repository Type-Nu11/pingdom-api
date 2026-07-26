package com.typenull.pingdom.boost.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record VerifiedBoostSelectionCreateRequest(
        @NotNull @Positive @Schema(example = "1") Long productId,
        @NotNull @Positive @Schema(example = "10") Long placeId,
        @NotBlank @Size(max = 64) @Schema(example = "boost-select-20260726-001") String idempotencyKey
) {
}
