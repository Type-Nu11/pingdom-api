package com.typenull.pingdom.place.outbox.information;

import com.typenull.pingdom.place.domain.place.information.report.PlaceInformationDisputeStatus;
import java.time.LocalDateTime;

public record PlaceInformationDisputeOutboxPayload(
        Long placeId,
        Long reportId,
        Long disputeId,
        Long disputedByUserId,
        PlaceInformationDisputeStatus status,
        LocalDateTime occurredAt
) {
}
