package com.typenull.pingdom.place.domain.place.operating;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 요일별 반복 영업 구간입니다. 요일·시작·종료는 필수이며 자정 통과 구간도 표현할 수 있습니다.
 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaceRegularOperatingHour {

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", length = 9, nullable = false)
    private DayOfWeek dayOfWeek;

    @Column(name = "opens_at", nullable = false)
    private LocalTime opensAt;

    @Column(name = "closes_at", nullable = false)
    private LocalTime closesAt;

    private PlaceRegularOperatingHour(DayOfWeek dayOfWeek, LocalTime opensAt, LocalTime closesAt) {
        this.dayOfWeek = Objects.requireNonNull(dayOfWeek, "dayOfWeek must not be null");
        this.opensAt = Objects.requireNonNull(opensAt, "opensAt must not be null");
        this.closesAt = Objects.requireNonNull(closesAt, "closesAt must not be null");
    }

    public static PlaceRegularOperatingHour of(DayOfWeek dayOfWeek, LocalTime opensAt, LocalTime closesAt) {
        return new PlaceRegularOperatingHour(dayOfWeek, opensAt, closesAt);
    }
}
