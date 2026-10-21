package com.typenull.pingdom.moderation.api.dto.place.map;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;
import com.typenull.pingdom.place.domain.place.discovery.PlaceDiscoveryStatus;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingStatus;

@Schema(description = "지도 경계 WGS84, CSS pixel 크기. 날짜변경선 횡단은 지원하지 않습니다.")
public record AdminPlaceMapRequest(
        @Schema(minimum = "-180", maximum = "180", example = "128.5") @NotNull Double west,
        @Schema(minimum = "-85.05112878", maximum = "85.05112878", example = "35.8") @NotNull Double south,
        @Schema(minimum = "-180", maximum = "180", example = "128.7") @NotNull Double east,
        @Schema(minimum = "-85.05112878", maximum = "85.05112878", example = "35.95") @NotNull Double north,
        @NotNull @Min(0) @Max(20) Integer zoom,
        @NotNull @Min(64) @Max(8192) Integer width,
        @NotNull @Min(64) @Max(8192) Integer height,
        @Size(max = 100) String keyword, @Size(max = 100) String category,
        PlaceDiscoveryStatus discoveryStatus, PlaceOperatingStatus operatingStatus
) {}
