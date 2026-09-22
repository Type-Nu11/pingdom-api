package com.typenull.pingdom.place.application.service.place.operating;

import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingException;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingStatus;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingTimeRange;
import com.typenull.pingdom.place.domain.place.operating.PlaceRegularOperatingHour;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 장소 운영 상태와 날짜별 예외·정규 영업시간을 결합해 조회 시점의 영업 여부를 계산합니다.
 * 해당 날짜의 예외가 있으면 정규 시간보다 우선하며 일치하는 일정이 없으면 영업하지 않는 것으로 처리합니다.
 */
@Component
public class PlaceOperatingHoursEvaluator {

    private final Clock clock;

    public PlaceOperatingHoursEvaluator(Clock clock) {
        this.clock = clock;
    }

    public PlaceCurrentOperatingState evaluate(MapPlace place) {
        return evaluate(place, LocalDateTime.now(clock));
    }

    public PlaceCurrentOperatingState evaluate(MapPlace place, LocalDateTime checkedAt) {
        Objects.requireNonNull(place, "place must not be null");
        LocalDateTime safeCheckedAt = Objects.requireNonNull(checkedAt, "checkedAt must not be null");
        if (place.getOperatingStatus() != PlaceOperatingStatus.OPERATING) {
            return new PlaceCurrentOperatingState(false, safeCheckedAt);
        }

        return new PlaceCurrentOperatingState(isOpenBySchedule(place, safeCheckedAt), safeCheckedAt);
    }

    private boolean isOpenBySchedule(MapPlace place, LocalDateTime checkedAt) {
        LocalDate date = checkedAt.toLocalDate();
        LocalTime time = checkedAt.toLocalTime();
        for (PlaceOperatingException exception : place.currentOperatingExceptions()) {
            if (!exception.getExceptionDate().equals(date)) {
                continue;
            }
            if (exception.isClosed()) {
                return false;
            }
            return exception.currentHours().stream()
                    .anyMatch(range -> contains(range.getOpensAt(), range.getClosesAt(), time));
        }

        DayOfWeek dayOfWeek = checkedAt.getDayOfWeek();
        DayOfWeek previousDay = dayOfWeek.minus(1);
        return place.currentRegularOperatingHours().stream()
                .anyMatch(hour -> matchesRegularHour(hour, dayOfWeek, previousDay, time));
    }

    private boolean matchesRegularHour(
            PlaceRegularOperatingHour hour,
            DayOfWeek dayOfWeek,
            DayOfWeek previousDay,
            LocalTime time
    ) {
        if (hour.getDayOfWeek() == dayOfWeek && contains(hour.getOpensAt(), hour.getClosesAt(), time)) {
            return true;
        }
        return hour.getDayOfWeek() == previousDay
                && crossesMidnight(hour.getOpensAt(), hour.getClosesAt())
                && time.isBefore(hour.getClosesAt());
    }

    /**
     * 시간 구간은 시작 포함·종료 제외로 평가하며 시작과 종료가 같으면 24시간으로 해석합니다.
     * 자정을 넘는 구간은 시작 이후 또는 종료 이전의 시각을 포함합니다.
     */
    private boolean contains(LocalTime opensAt, LocalTime closesAt, LocalTime checkedAt) {
        if (opensAt.equals(closesAt)) {
            return true;
        }
        if (crossesMidnight(opensAt, closesAt)) {
            return !checkedAt.isBefore(opensAt) || checkedAt.isBefore(closesAt);
        }
        return !checkedAt.isBefore(opensAt) && checkedAt.isBefore(closesAt);
    }

    private boolean crossesMidnight(LocalTime opensAt, LocalTime closesAt) {
        return closesAt.isBefore(opensAt);
    }
}
