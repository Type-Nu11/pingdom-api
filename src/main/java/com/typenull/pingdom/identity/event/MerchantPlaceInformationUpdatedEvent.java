package com.typenull.pingdom.identity.event;

import java.time.LocalDateTime;

public record MerchantPlaceInformationUpdatedEvent(
        Long merchantUserId,
        Long placeId,
        boolean created,
        LocalDateTime occurredAt
) {
}
