package com.typenull.pingdom.availability.api.dto;

import com.typenull.pingdom.availability.domain.AvailabilityProductType;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record AvailabilityUpsertRequest(
        @NotNull Long placeId,
        Long productId,
        AvailabilityProductType productType,
        @NotNull @Future LocalDateTime startsAt,
        @NotNull @Future LocalDateTime endsAt,
        @Min(1) int totalCapacity
) {
    public AvailabilityUpsertRequest(Long placeId, LocalDateTime startsAt, LocalDateTime endsAt, int totalCapacity) {
        this(placeId, null, AvailabilityProductType.GENERAL, startsAt, endsAt, totalCapacity);
    }
}
