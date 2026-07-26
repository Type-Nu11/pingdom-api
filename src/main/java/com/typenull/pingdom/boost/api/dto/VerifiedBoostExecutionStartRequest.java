package com.typenull.pingdom.boost.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record VerifiedBoostExecutionStartRequest(
        @NotNull @Positive @Schema(example = "1") Long selectionId
) {
}
