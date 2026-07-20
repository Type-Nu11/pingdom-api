package com.typenull.pingdom.place.outbox;

import com.typenull.pingdom.place.domain.place.information.PlaceInformationVerificationStatus;
import java.time.LocalDateTime;

public record PlaceInformationVerificationUpdatedOutboxPayload(
        Long placeId,
        Long evidenceId,
        PlaceInformationVerificationStatus fromStatus,
        PlaceInformationVerificationStatus toStatus,
        Long reviewedByAdminUserId,
        LocalDateTime reviewedAt
) {
}
