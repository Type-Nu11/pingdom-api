package com.typenull.pingdom.verification.api.dto;

import com.typenull.pingdom.verification.application.VisitVerificationProperties;
import com.typenull.pingdom.verification.domain.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** 앱이 체류 진행 상황을 표시하고 재관측 시점을 결정하는 응답. */
public record VisitVerificationSessionResponse(
        Long id,
        Long touristUserId,
        Long placeId,
        VisitVerificationSessionStatus status,
        Instant startedAt,
        Instant expiresAt,
        @Schema(nullable = true) Instant completedAt,
        @Schema(description = "세션 생성 시 확정된 장소 반경 기준(미터)", example = "500") double requiredRadiusMeters,
        @Schema(description = "세션 생성 시 확정된 연속 체류 기준(초)", example = "30") long requiredDwellSeconds,
        double latestDistanceMeters,
        long verifiedDwellSeconds,
        @Schema(nullable = true) Instant nextObservationRecommendedAt,
        long remainingSeconds,
        @Schema(nullable = true) Long completedCheckInId,
        boolean reviewEligible
) {
    /**
     * 활성 세션에만 다음 관측 권장 시각을 제공하고 남은 초의 최솟값은 0으로 제한.
     * reviewEligible은 세션 COMPLETED 여부를 뜻하며 별도 리뷰 존재 여부는 조회 대상에서 제외.
     */
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
