package com.typenull.pingdom.place.api.dto.conversion;

import com.typenull.pingdom.place.domain.conversion.MapLinkConversionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MapLinkConversionRequest(
        @NotNull MapLinkConversionType linkType,
        @NotBlank String provider,
        @NotBlank String requestId) {}
