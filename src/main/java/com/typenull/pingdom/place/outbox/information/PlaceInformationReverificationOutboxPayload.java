package com.typenull.pingdom.place.outbox.information;

import com.typenull.pingdom.place.domain.place.information.reverification.PlaceInformationReverificationRequest;
import com.typenull.pingdom.place.domain.place.information.reverification.PlaceInformationReverificationStatus;
import java.time.LocalDateTime;

public record PlaceInformationReverificationOutboxPayload(
        Long requestId, Long placeId, String placeName, Long merchantOwnerUserId,
        PlaceInformationReverificationStatus status, int reminderCount, LocalDateTime occurredAt
) {
    public static PlaceInformationReverificationOutboxPayload from(
            PlaceInformationReverificationRequest request, LocalDateTime occurredAt
    ) {
        return new PlaceInformationReverificationOutboxPayload(
                request.getId(), request.getPlace().getId(), request.getPlace().getName(), request.getMerchantOwnerUserId(),
                request.getStatus(), request.getReminderCount(), occurredAt
        );
    }
}
