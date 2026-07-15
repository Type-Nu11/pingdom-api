package com.typenull.pingdom.identity.api.dto.merchant;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MerchantPlaceClaimRequest(
        @NotNull Long placeId,
        @NotBlank @Size(max = 500) @Schema(minLength = 1) String reason
) {
}
