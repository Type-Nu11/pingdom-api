package com.typenull.pingdom.boost.api.dto;

import com.typenull.pingdom.boost.domain.MerchantVerifiedBoostSelection;
import java.time.LocalDateTime;

public record VerifiedBoostSelectionResponse(
        Long id,
        Long productId,
        Long placeId,
        LocalDateTime selectedAt
) {
    public static VerifiedBoostSelectionResponse from(MerchantVerifiedBoostSelection selection) {
        return new VerifiedBoostSelectionResponse(selection.getId(), selection.getProductId(),
                selection.getPlaceId(), selection.getSelectedAt());
    }
}
