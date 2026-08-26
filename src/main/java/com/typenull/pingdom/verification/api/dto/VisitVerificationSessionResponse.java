package com.typenull.pingdom.verification.api.dto;

import com.typenull.pingdom.verification.application.VisitVerificationProperties;
import com.typenull.pingdom.verification.domain.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** 앱이 체류 진행 상황을 표시하고 재관측 시점을 결정하는 응답입니다. */
public record VisitVerificationSessionResponse(
        Long id,
        Long touristUserId,
        Long placeId,
        VisitVerificationSessionStatus status,
        Instant startedAt,
        Instant expiresAt,
        @Schema(nullable = true) Instant completedAt,
        double requiredRadiusMeters,
        long requiredDwellSeconds,
        double latestDistanceMeters,
        long verifiedDwellSeconds,
        @Schema(nullable = true) Instant nextObservationRecommendedAt,
        long remainingSeconds,
        @Schema(nullable = true) Long completedCheckInId,
        boolean reviewEligible
) {
    public static VisitVerificationSessionResponse from(VisitVerificationSession session,
            VisitVerificationProperties properties) {
        boolean completed = session.getStatus() == VisitVerificationSessionStatus.COMPLETED;
        Instant nextObservation = session.isActive()
                ? session.getLastVerifiedAt().plus(properties.observationInterval())
                : null;
        long remainingSeconds = Math.max(0, session.getRequiredDwellSeconds() - session.getVerifiedDwellSeconds());
        return new VisitVerificationSessionResponse(session.getId(), session.getTouristUserId(), session.getPlaceId(),
                session.getStatus(), session.getStartedAt(), session.getExpiresAt(), session.getCompletedAt(),
                session.getRequiredRadiusMeters(), session.getRequiredDwellSeconds(), session.getLastDistanceMeters(),
                session.getVerifiedDwellSeconds(), nextObservation, remainingSeconds, session.getCompletedCheckInId(),
                completed);
    }
}
