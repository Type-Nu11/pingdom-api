package com.typenull.pingdom.consultation.api.dto;

import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VoiceAiMessageRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 2_000) String text,
        @Schema(description = "최종 envelope.id와 동일한 요청 ID. 재시도 시 동일 text와 함께 유지합니다.", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank @Size(max = 128)
        @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$") String requestId
) {
}
