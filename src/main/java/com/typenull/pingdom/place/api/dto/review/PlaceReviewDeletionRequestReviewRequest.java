package com.typenull.pingdom.place.api.dto.review;

import com.typenull.pingdom.place.domain.review.PlaceReviewDeletionRequestStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PlaceReviewDeletionRequestReviewRequest(
        @NotNull PlaceReviewDeletionRequestStatus decision,
        @Size(max = 500) String reviewNote
) {}
