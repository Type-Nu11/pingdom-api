package com.typenull.pingdom.place.outbox.information;

import com.typenull.pingdom.place.domain.place.information.report.PlaceInformationReportStatus;
import java.time.LocalDateTime;

public record PlaceInformationReportOutboxPayload(
        Long placeId,
        Long reportId,
        Long reporterUserId,
        PlaceInformationReportStatus status,
        LocalDateTime occurredAt
) {
}
