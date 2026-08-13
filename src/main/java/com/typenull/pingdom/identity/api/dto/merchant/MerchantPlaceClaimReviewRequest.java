package com.typenull.pingdom.identity.api.dto.merchant;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MerchantPlaceClaimReviewRequest(
        @NotNull Boolean approved,
        @NotBlank @Size(max = 500) @Schema(minLength = 1) String reason,
        Long reviewedVersion
) {
    public MerchantPlaceClaimReviewRequest(Boolean approved, String reason) {
        this(approved, reason, null);
    }
}
