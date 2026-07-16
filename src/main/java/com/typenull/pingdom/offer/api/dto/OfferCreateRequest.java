package com.typenull.pingdom.offer.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record OfferCreateRequest(
        @NotNull @Positive @Schema(example = "10") Long placeId,
        @NotBlank @Size(max = 100) @Schema(example = "관광객 웰컴 음료") String title,
        @NotBlank @Size(max = 1000) @Schema(example = "여행 중인 관광객에게 제공하는 한정 Offer입니다.") String description,
        @NotBlank @Size(max = 500) @Schema(example = "음료 1잔 무료") String benefitDescription,
        @NotNull @Schema(example = "2026-08-01T09:00:00") LocalDateTime startsAt,
        @NotNull @Schema(example = "2026-08-31T23:59:59") LocalDateTime endsAt,
        @Min(1) @Max(100000) @Schema(example = "100") int totalQuantity,
        @Min(1) @Max(365) @Schema(example = "7") int couponValidityDays
) {
}
