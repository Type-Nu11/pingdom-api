package com.typenull.pingdom.identity.event;

import com.typenull.pingdom.identity.domain.merchant.MerchantOperationalQualityStatus;
import java.time.LocalDateTime;

public record MerchantOperationalQualityUpdatedEvent(
        Long merchantOwnerUserId,
        Long placeId,
        MerchantOperationalQualityStatus beforeStatus,
        MerchantOperationalQualityStatus afterStatus,
        LocalDateTime occurredAt
) {
}
