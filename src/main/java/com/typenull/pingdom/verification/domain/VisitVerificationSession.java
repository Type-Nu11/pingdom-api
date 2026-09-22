package com.typenull.pingdom.verification.domain;

import jakarta.persistence.*;
import java.time.*;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 원본 GPS 좌표는 보관하지 않고 서버가 판정한 체류 결과만 저장하는 방문 인증 세션. */
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

    /**
     * 시작 시점의 반경·체류 시간·TTL을 세션에 복사해 이후 전역 설정 변경과 분리.
     * 체류 누적은 0초로 시작하며 인증 날짜와 관측/서버 시각을 따로 보관.
     */
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

    /** STARTED와 IN_PROGRESS만 후속 관측을 받을 수 있는 진행 상태로 분류. */
    public boolean isActive() {
        return status == VisitVerificationSessionStatus.STARTED || status == VisitVerificationSessionStatus.IN_PROGRESS;
    }

    /** TTL의 종료 시각과 같거나 이후이면 만료로 판정. 상태 자체는 유지. */
    public boolean isExpiredAt(Instant now) {
        return !now.isBefore(expiresAt);
    }

    /** 마지막 서버 확인 이후 허용 간격을 엄격히 초과했는지 판정. 정확한 경계는 허용. */
    public boolean hasObservationGapExceeded(Instant now, Duration maxObservationGap) {
        return now.isAfter(lastVerifiedAt.plus(maxObservationGap));
    }

    /**
     * 검증된 관측을 기록하고 시작부터 서버 수신 시각까지의 경과 초를 체류 시간으로 갱신.
     * 연속성·반경·진행 상태 검사는 호출 서비스가 먼저 수행해야 함.
     */
    public void recordObservation(Instant observedAt, Instant serverNow, double distanceMeters) {
        lastObservedAt = Objects.requireNonNull(observedAt);
        lastVerifiedAt = Objects.requireNonNull(serverNow);
        lastDistanceMeters = distanceMeters;
        verifiedDwellSeconds = Math.max(0, Duration.between(startedAt, serverNow).toSeconds());
        status = VisitVerificationSessionStatus.IN_PROGRESS;
    }

    /** 반경 이탈 관측의 시각·거리·서버 경과 시간을 남기고 재개 불가능한 이탈 상태로 변경. */
    public void loseProximity(Instant observedAt, Instant serverNow, double distanceMeters) {
        lastObservedAt = Objects.requireNonNull(observedAt);
        lastVerifiedAt = Objects.requireNonNull(serverNow);
        lastDistanceMeters = distanceMeters;
        verifiedDwellSeconds = Math.max(0, Duration.between(startedAt, serverNow).toSeconds());
        status = VisitVerificationSessionStatus.PROXIMITY_LOST;
    }

    /** 마지막 서버 확인 시각과 만료 상태를 기록. 만료 조건의 판정은 호출자가 담당. */
    public void expire(Instant serverNow) {
        lastVerifiedAt = Objects.requireNonNull(serverNow);
        status = VisitVerificationSessionStatus.EXPIRED;
    }

    /** 저장이 완료된 체크인 ID와 완료 시각을 연결하고 서버 기준 체류 초를 갱신. */
    public void complete(Instant serverNow, Long checkInId) {
        completedAt = Objects.requireNonNull(serverNow);
        completedCheckInId = Objects.requireNonNull(checkInId);
        verifiedDwellSeconds = Math.max(0, Duration.between(startedAt, serverNow).toSeconds());
        status = VisitVerificationSessionStatus.COMPLETED;
    }
}
