package com.typenull.pingdom.campaign.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BrandCreateRequest(
        @NotBlank @Size(max = 100) @Schema(example = "핑덤 스튜디오") String name,
        @Size(max = 1000) @Schema(example = "지역 경험을 만드는 라이프스타일 브랜드") String description,
        @Size(max = 500) @Schema(example = "https://cdn.example.com/brands/pingdom.png") String logoUrl
) {
}
