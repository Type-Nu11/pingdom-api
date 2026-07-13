package com.typenull.pingdom.place.domain.place;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.LocalTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaceOperatingTimeRange {

    @Column(name = "opens_at", nullable = false)
    private LocalTime opensAt;

    @Column(name = "closes_at", nullable = false)
    private LocalTime closesAt;

    private PlaceOperatingTimeRange(LocalTime opensAt, LocalTime closesAt) {
        this.opensAt = Objects.requireNonNull(opensAt, "opensAt must not be null");
        this.closesAt = Objects.requireNonNull(closesAt, "closesAt must not be null");
    }

    public static PlaceOperatingTimeRange of(LocalTime opensAt, LocalTime closesAt) {
        return new PlaceOperatingTimeRange(opensAt, closesAt);
    }
}
