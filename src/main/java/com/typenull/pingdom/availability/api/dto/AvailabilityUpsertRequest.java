package com.typenull.pingdom.availability.api.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record AvailabilityUpsertRequest(
        @NotNull Long placeId,
        @NotNull @Future LocalDateTime startsAt,
        @NotNull @Future LocalDateTime endsAt,
        @Min(1) int totalCapacity
) {}
