package com.typenull.pingdom.place.outbox;

import com.typenull.pingdom.place.domain.conversion.PlaceConversionEventType;
import java.time.LocalDateTime;

public record PlaceConversionEventOutboxPayload(
        Long conversionEventId,
        Long userId,
        Long placeId,
        PlaceConversionEventType conversionType,
        Long sourceId,
        LocalDateTime occurredAt
) {
}
