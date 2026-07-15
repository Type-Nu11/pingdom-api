package com.typenull.pingdom.identity.api.dto.merchant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MerchantVerificationReviewRequest(
        @NotNull Boolean identityApproved,
        @NotNull Boolean businessApproved,
        @NotBlank @Size(max = 500) String reason
) {
}
