package com.typenull.pingdom.identity.api.dto.merchant;

import jakarta.validation.constraints.Size;

public record MerchantOwnerReviewRequest(
        @Size(max = 500) String reason
) {
}
