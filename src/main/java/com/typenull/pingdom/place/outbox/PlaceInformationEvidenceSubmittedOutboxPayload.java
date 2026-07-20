package com.typenull.pingdom.place.outbox;

import com.typenull.pingdom.place.domain.place.information.PlaceInformationEvidenceType;
import com.typenull.pingdom.place.domain.place.information.PlaceInformationSourceType;
import java.time.LocalDateTime;

public record PlaceInformationEvidenceSubmittedOutboxPayload(
        Long placeId,
        Long evidenceId,
        PlaceInformationSourceType sourceType,
        PlaceInformationEvidenceType evidenceType,
        Long submittedByUserId,
        LocalDateTime submittedAt
) {
}
