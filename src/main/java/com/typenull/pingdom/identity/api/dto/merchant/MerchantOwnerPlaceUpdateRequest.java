package com.typenull.pingdom.identity.api.dto.merchant;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record MerchantOwnerPlaceUpdateRequest(
        @Size(max = 100) Set<@NotNull Long> placeIds,
        @Size(max = 500) String reason
) {
    public Set<Long> normalizedPlaceIds() {
        return placeIds == null ? Set.of() : Set.copyOf(placeIds);
    }
}
