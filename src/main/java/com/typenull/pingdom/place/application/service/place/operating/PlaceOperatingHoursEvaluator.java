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
