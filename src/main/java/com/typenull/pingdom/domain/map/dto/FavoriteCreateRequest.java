package com.typenull.pingdom.domain.map.dto;

import jakarta.validation.constraints.NotNull;

public record FavoriteCreateRequest(
        @NotNull(message = "placeId는 필수입니다.")
        Long placeId
) {
}

