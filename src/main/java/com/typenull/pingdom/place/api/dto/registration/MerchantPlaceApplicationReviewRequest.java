package com.typenull.pingdom.place.api.dto.registration;

import jakarta.validation.constraints.Size;

public record MerchantPlaceApplicationReviewRequest(@Size(max = 500) String reason) {
}
