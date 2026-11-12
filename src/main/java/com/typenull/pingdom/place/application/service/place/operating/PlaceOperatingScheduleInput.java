package com.typenull.pingdom.place.application.service.place.operating;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

/** HTTP 요청 타입과 무관하게 영업일정 전체 교체 값을 전달합니다. */
public record PlaceOperatingScheduleInput(
        Set<RegularHour> regularHours,
        Set<ExceptionDay> exceptions
) {
    public record RegularHour(DayOfWeek dayOfWeek, LocalTime opensAt, LocalTime closesAt) {
    }

    public record ExceptionDay(LocalDate date, boolean closed, Set<TimeRange> hours) {
    }

    public record TimeRange(LocalTime opensAt, LocalTime closesAt) {
    }
}
