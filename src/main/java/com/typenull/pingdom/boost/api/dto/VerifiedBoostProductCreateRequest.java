package com.typenull.pingdom.boost.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record VerifiedBoostProductCreateRequest(
        @NotBlank @Size(max = 100) @Schema(example = "Verified Boost 7일") String name,
        @NotBlank @Size(max = 500) @Schema(example = "검증된 장소의 추천 노출을 7일간 강화합니다.") String description,
        @NotNull @Positive @Schema(example = "30000", description = "KRW 기준 가격") Long priceAmount,
        @NotNull @Min(1) @Max(365) @Schema(example = "7") Integer durationDays
) {
}
