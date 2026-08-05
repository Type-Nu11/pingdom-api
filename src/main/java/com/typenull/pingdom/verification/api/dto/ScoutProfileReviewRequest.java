package com.typenull.pingdom.verification.api.dto;

import jakarta.validation.constraints.Size;

public record ScoutProfileReviewRequest(
        @Size(max = 500) String reason
) {
}
