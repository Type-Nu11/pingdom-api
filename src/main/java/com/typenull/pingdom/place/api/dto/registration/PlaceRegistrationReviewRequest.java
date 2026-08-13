package com.typenull.pingdom.place.api.dto.registration;

import jakarta.validation.constraints.Size;

public record PlaceRegistrationReviewRequest(@Size(max = 500) String reason) {
}
