package com.typenull.pingdom.place.api.dto.registration;

import com.typenull.pingdom.place.domain.registration.PlaceRegistrationOperatingStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

public record PlaceRegistrationOperatingDay(
        @NotNull DayOfWeek dayOfWeek,
        @NotNull PlaceRegistrationOperatingStatus status,
        LocalTime opensAt,
        LocalTime closesAt,
        @Valid List<BreakTime> breakTimes
) {
    public record BreakTime(@NotNull LocalTime opensAt, @NotNull LocalTime closesAt) {}
}
