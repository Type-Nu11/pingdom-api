package com.typenull.pingdom.boost.api.dto;

import com.typenull.pingdom.boost.domain.VerifiedBoostExecution;
import com.typenull.pingdom.boost.domain.VerifiedBoostExecutionStatus;
import java.time.LocalDateTime;

public record VerifiedBoostExecutionResponse(
        Long id,
        Long selectionId,
        Long productId,
        Long placeId,
        VerifiedBoostExecutionStatus status,
        LocalDateTime startedAt,
        LocalDateTime endsAt,
        LocalDateTime stoppedAt
) {
    public static VerifiedBoostExecutionResponse from(VerifiedBoostExecution execution, LocalDateTime now) {
        return new VerifiedBoostExecutionResponse(
                execution.getId(), execution.getSelectionId(), execution.getProductId(), execution.getPlaceId(),
                execution.effectiveStatusAt(now), execution.getStartedAt(), execution.getEndsAt(),
                execution.getStoppedAt());
    }
}
