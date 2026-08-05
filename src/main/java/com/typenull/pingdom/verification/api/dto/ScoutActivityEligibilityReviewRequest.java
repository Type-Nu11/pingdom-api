package com.typenull.pingdom.verification.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ScoutActivityEligibilityReviewRequest(
        @NotBlank @Size(max = 500) String reason
) {
}
