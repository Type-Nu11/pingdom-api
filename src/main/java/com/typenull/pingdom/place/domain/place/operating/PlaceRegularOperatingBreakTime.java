package com.typenull.pingdom.place.domain.place.operating;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.time.DayOfWeek;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 요일별 반복 휴게 구간을 저장하는 값 객체입니다.
 * 생성자는 전달값을 그대로 보관하며 null·시간 순서 검증을 수행하지 않습니다.
 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlaceRegularOperatingBreakTime {
    @Enumerated(EnumType.STRING) @Column(name = "day_of_week", length = 9, nullable = false)
    private DayOfWeek dayOfWeek;
    @Column(name = "opens_at", nullable = false) private LocalTime opensAt;
    @Column(name = "closes_at", nullable = false) private LocalTime closesAt;

    private PlaceRegularOperatingBreakTime(DayOfWeek dayOfWeek, LocalTime opensAt, LocalTime closesAt) {
        this.dayOfWeek = dayOfWeek; this.opensAt = opensAt; this.closesAt = closesAt;
    }
    public static PlaceRegularOperatingBreakTime of(DayOfWeek dayOfWeek, LocalTime opensAt, LocalTime closesAt) {
        return new PlaceRegularOperatingBreakTime(dayOfWeek, opensAt, closesAt);
    }
}
