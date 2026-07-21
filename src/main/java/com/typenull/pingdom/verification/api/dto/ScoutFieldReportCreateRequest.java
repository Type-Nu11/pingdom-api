package com.typenull.pingdom.verification.api.dto;

import com.typenull.pingdom.verification.domain.ScoutFieldReportType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ScoutFieldReportCreateRequest(
        @NotNull Long placeId,
        @NotNull ScoutFieldReportType reportType,
        @NotBlank @Size(max = 1000) String description,
        @Size(max = 500)
        @Pattern(regexp = "^https://[^\\s]+$", message = "증빙 URL은 HTTPS 형식이어야 합니다.")
        String evidenceUrl
) {}
