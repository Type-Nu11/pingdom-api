package com.typenull.pingdom.place.api.dto.review;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PlaceReviewDeletionRequestCreateRequest(
        @NotBlank @Size(max = 500) String requestReason
) {}
