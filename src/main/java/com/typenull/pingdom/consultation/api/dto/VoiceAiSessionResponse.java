package com.typenull.pingdom.consultation.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.OffsetDateTime;

public record VoiceAiSessionResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String sessionId,
        @Schema(description = "만료 시각. ISO-8601 offset 포함. 현재 시각이 이 값 이상이면 만료됩니다.",
                type = "string", format = "date-time", example = "2026-09-17T12:05:00+09:00",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        OffsetDateTime expiresAt
) {
}
