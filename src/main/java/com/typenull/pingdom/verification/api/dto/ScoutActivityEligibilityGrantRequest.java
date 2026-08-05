package com.typenull.pingdom.verification.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record ScoutActivityEligibilityGrantRequest(
        @NotNull LocalDateTime eligibleFrom,
        LocalDateTime eligibleUntil,
        @Size(max = 500) String reason
) {
}
