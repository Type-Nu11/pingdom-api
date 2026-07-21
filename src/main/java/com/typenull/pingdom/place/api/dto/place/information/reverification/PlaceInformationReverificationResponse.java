package com.typenull.pingdom.place.api.dto.place.information.reverification;

import com.typenull.pingdom.place.domain.place.information.reverification.PlaceInformationReverificationRequest;
import com.typenull.pingdom.place.domain.place.information.reverification.PlaceInformationReverificationStatus;
import java.time.LocalDateTime;

public record PlaceInformationReverificationResponse(
        Long requestId, Long placeId, Long merchantOwnerUserId,
        PlaceInformationReverificationStatus status, String reason,
        LocalDateTime requestedAt, LocalDateTime dueAt,
        LocalDateTime lastRemindedAt, int reminderCount,
        LocalDateTime respondedAt, String responseNote, LocalDateTime completedAt
) {
    public static PlaceInformationReverificationResponse from(PlaceInformationReverificationRequest request) {
        return new PlaceInformationReverificationResponse(
                request.getId(), request.getPlace().getId(), request.getMerchantOwnerUserId(),
                request.getStatus(), request.getReason(), request.getRequestedAt(), request.getDueAt(),
                request.getLastRemindedAt(), request.getReminderCount(), request.getRespondedAt(),
                request.getResponseNote(), request.getCompletedAt()
        );
    }
}
