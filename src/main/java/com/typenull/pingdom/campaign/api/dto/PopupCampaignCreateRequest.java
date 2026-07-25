package com.typenull.pingdom.campaign.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public record PopupCampaignCreateRequest(
        @NotNull @Positive @Schema(example = "1") Long brandId,
        @NotNull @Positive @Schema(example = "10") Long placeId,
        @NotBlank @Size(max = 150) @Schema(example = "성수 여름 팝업") String title,
        @NotBlank @Size(max = 2000) String description,
        @NotNull @Schema(example = "2026-08-01T10:00:00") LocalDateTime startsAt,
        @NotNull @Schema(example = "2026-08-31T20:00:00") LocalDateTime endsAt
) {
}
