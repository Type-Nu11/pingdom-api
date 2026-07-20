package com.typenull.pingdom.verification.domain;

import jakarta.persistence.*;
import java.time.*;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

    public static LocationCheckIn proximityMatched(Long touristUserId, Long placeId, LocalDate checkInDate,
            Instant observedAt, Instant recordedAt, double distanceMeters) {
        LocationCheckIn checkIn = new LocationCheckIn();
        checkIn.touristUserId = Objects.requireNonNull(touristUserId);
        checkIn.placeId = Objects.requireNonNull(placeId);
        checkIn.checkInDate = Objects.requireNonNull(checkInDate);
        checkIn.observedAt = Objects.requireNonNull(observedAt);
        checkIn.recordedAt = Objects.requireNonNull(recordedAt);
        checkIn.distanceMeters = distanceMeters;
        checkIn.status = LocationCheckInStatus.PROXIMITY_MATCHED;
        return checkIn;
    }
}
