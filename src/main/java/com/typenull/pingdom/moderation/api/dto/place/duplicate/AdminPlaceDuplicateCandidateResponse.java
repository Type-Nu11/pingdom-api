package com.typenull.pingdom.moderation.api.dto.place.duplicate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminPlaceDuplicateCandidateResponse(
        Long candidateId,
        Long leftPlaceId,
        Long rightPlaceId,
        String matchReason,
        BigDecimal confidenceScore,
        Integer distanceMeters,
        String status,
        Long reviewedByAdminUserId,
        String reviewNote,
        Long mergeHistoryId,
        LocalDateTime detectedAt,
        LocalDateTime reviewedAt,
        LocalDateTime updatedAt
) {
}
