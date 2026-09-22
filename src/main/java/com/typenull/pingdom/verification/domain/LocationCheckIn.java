package com.typenull.pingdom.verification.domain;

import jakarta.persistence.*;
import java.time.*;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 관광객의 장소 체크인 기록으로 관측 시각과 서버 기록 시각을 구분.
 * 근접 일치와 체류 인증 완료 상태를 보관하며, 유효한 위치·기간인지의 판정은 생성 전 서비스에서 수행.
 */
@Entity
@Getter
@Table(name = "location_check_in")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LocationCheckIn {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tourist_user_id", nullable = false)
    private Long touristUserId;

    @Column(name = "place_id", nullable = false)
    private Long placeId;

    @Column(name = "check_in_date", nullable = false)
    private LocalDate checkInDate;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "distance_meters", nullable = false)
    private double distanceMeters;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LocationCheckInStatus status;

    /** 근접 반경 판정을 통과한 체크인을 생성. 체류 인증 완료 여부는 별도. */
    public static LocationCheckIn proximityMatched(Long touristUserId, Long placeId, LocalDate checkInDate,
            Instant observedAt, Instant recordedAt, double distanceMeters) {
        return create(touristUserId, placeId, checkInDate, observedAt, recordedAt, distanceMeters,
                LocationCheckInStatus.PROXIMITY_MATCHED);
    }

    /** 체류 인증 세션을 완료한 결과를 DWELL_VERIFIED 체크인으로 기록. */
    public static LocationCheckIn dwellVerified(Long touristUserId, Long placeId, LocalDate checkInDate,
            Instant observedAt, Instant recordedAt, double distanceMeters) {
        return create(touristUserId, placeId, checkInDate, observedAt, recordedAt, distanceMeters,
                LocationCheckInStatus.DWELL_VERIFIED);
    }

    /** 사용자·장소·날짜·시각의 null을 거부하고 호출자가 확정한 거리(미터)와 상태를 보관. */
    private static LocationCheckIn create(Long touristUserId, Long placeId, LocalDate checkInDate,
            Instant observedAt, Instant recordedAt, double distanceMeters, LocationCheckInStatus status) {
        LocationCheckIn checkIn = new LocationCheckIn();
        checkIn.touristUserId = Objects.requireNonNull(touristUserId);
        checkIn.placeId = Objects.requireNonNull(placeId);
        checkIn.checkInDate = Objects.requireNonNull(checkInDate);
        checkIn.observedAt = Objects.requireNonNull(observedAt);
        checkIn.recordedAt = Objects.requireNonNull(recordedAt);
        checkIn.distanceMeters = distanceMeters;
        checkIn.status = status;
        return checkIn;
    }
}
