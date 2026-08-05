package com.typenull.pingdom.verification.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ScoutProfileRequest(
        @NotBlank @Size(max = 100) String displayName,
        @Size(max = 1000) String introduction
) {
}
