package com.typenull.pingdom.consultation.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VoiceAiMessageRequest(
        @NotBlank @Size(max = 2_000) String text,
        @NotBlank @Size(max = 128)
        @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$") String requestId
) {
}
