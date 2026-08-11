package com.typenull.pingdom.place.api.dto.conversion;

import com.typenull.pingdom.place.domain.conversion.MapLinkConversionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

public record MapLinkConversionRequest(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "지도 링크 전환 유형")
        @NotNull MapLinkConversionType linkType,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "지도 제공자", example = "KAKAO")
        @NotBlank @Size(max = 30) String provider,
        @Schema(
                requiredMode = Schema.RequiredMode.REQUIRED,
                description = "같은 사용자·장소·전환 유형의 재시도에서 재사용하는 멱등성 식별자",
                example = "9f7263d5-65f1-4834-9ca3-86ad2fc4e7d0"
        )
        @NotBlank @Size(max = 128) String requestId) {}
