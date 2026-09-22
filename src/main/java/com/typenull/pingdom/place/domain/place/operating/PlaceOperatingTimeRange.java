package com.typenull.pingdom.place.domain.place.operating;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.LocalTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 날짜별 예외 일정에 사용하는 시작·종료 시각 값입니다.
 * null 시각을 거부하지만 자정 통과나 두 시각의 동일 여부는 여기서 제한하지 않습니다.
 */
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
