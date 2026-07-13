package com.typenull.pingdom.identity.api.dto.merchant;

import jakarta.validation.constraints.Size;
import java.util.Set;

public record MerchantOwnerReviewRequest(
        @Size(max = 500) String reason,
        @Size(max = 100) Set<Long> placeIds
) {
    public Set<Long> normalizedPlaceIds() {
        return placeIds == null ? Set.of() : Set.copyOf(placeIds);
    }
}
