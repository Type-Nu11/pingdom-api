package com.typenull.pingdom.verification.domain;

import jakarta.persistence.*;
import java.time.*;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 원본 GPS 좌표는 보관하지 않고 서버가 판정한 체류 결과만 저장하는 방문 인증 세션입니다. */
@Entity
@Getter
@Table(name = "visit_verification_session")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VisitVerificationSession {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tourist_user_id", nullable = false)
    private Long touristUserId;

    @Column(name = "place_id", nullable = false)
    private Long placeId;

    @Column(name = "verification_date", nullable = false)
    private LocalDate verificationDate;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "last_observed_at", nullable = false)
    private Instant lastObservedAt;

    @Column(name = "last_verified_at", nullable = false)
    private Instant lastVerifiedAt;

    @Column(name = "last_distance_meters", nullable = false)
    private double lastDistanceMeters;

    @Column(name = "required_radius_meters", nullable = false)
    private double requiredRadiusMeters;

    @Column(name = "required_dwell_seconds", nullable = false)
    private long requiredDwellSeconds;

    @Column(name = "verified_dwell_seconds", nullable = false)
    private long verifiedDwellSeconds;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VisitVerificationSessionStatus status;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "completed_check_in_id")
    private Long completedCheckInId;

    public static VisitVerificationSession start(Long touristUserId, Long placeId, LocalDate verificationDate,
            Instant observedAt, Instant serverNow, double distanceMeters, double requiredRadiusMeters,
            Duration requiredDwell, Duration sessionTtl) {
        VisitVerificationSession session = new VisitVerificationSession();
        session.touristUserId = Objects.requireNonNull(touristUserId);
        session.placeId = Objects.requireNonNull(placeId);
        session.verificationDate = Objects.requireNonNull(verificationDate);
        session.startedAt = Objects.requireNonNull(serverNow);
        session.expiresAt = serverNow.plus(Objects.requireNonNull(sessionTtl));
        session.lastObservedAt = Objects.requireNonNull(observedAt);
        session.lastVerifiedAt = serverNow;
        session.lastDistanceMeters = distanceMeters;
        session.requiredRadiusMeters = requiredRadiusMeters;
        session.requiredDwellSeconds = requiredDwell.toSeconds();
        session.verifiedDwellSeconds = 0;
        session.status = VisitVerificationSessionStatus.STARTED;
        return session;
    }

    public boolean isActive() {
        return status == VisitVerificationSessionStatus.STARTED || status == VisitVerificationSessionStatus.IN_PROGRESS;
    }

    public boolean isExpiredAt(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public boolean hasObservationGapExceeded(Instant now, Duration maxObservationGap) {
        return now.isAfter(lastVerifiedAt.plus(maxObservationGap));
    }

    public void recordObservation(Instant observedAt, Instant serverNow, double distanceMeters) {
        lastObservedAt = Objects.requireNonNull(observedAt);
        lastVerifiedAt = Objects.requireNonNull(serverNow);
        lastDistanceMeters = distanceMeters;
        verifiedDwellSeconds = Math.max(0, Duration.between(startedAt, serverNow).toSeconds());
        status = VisitVerificationSessionStatus.IN_PROGRESS;
    }

    public void loseProximity(Instant observedAt, Instant serverNow, double distanceMeters) {
        lastObservedAt = Objects.requireNonNull(observedAt);
        lastVerifiedAt = Objects.requireNonNull(serverNow);
        lastDistanceMeters = distanceMeters;
        verifiedDwellSeconds = Math.max(0, Duration.between(startedAt, serverNow).toSeconds());
        status = VisitVerificationSessionStatus.PROXIMITY_LOST;
    }

    public void expire(Instant serverNow) {
        lastVerifiedAt = Objects.requireNonNull(serverNow);
        status = VisitVerificationSessionStatus.EXPIRED;
    }

    public void complete(Instant serverNow, Long checkInId) {
        completedAt = Objects.requireNonNull(serverNow);
        completedCheckInId = Objects.requireNonNull(checkInId);
        verifiedDwellSeconds = Math.max(0, Duration.between(startedAt, serverNow).toSeconds());
        status = VisitVerificationSessionStatus.COMPLETED;
    }
}
