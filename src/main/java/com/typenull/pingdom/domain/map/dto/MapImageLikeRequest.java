package com.typenull.pingdom.domain.map.dto;

import jakarta.validation.constraints.NotNull;

public record MapImageLikeRequest(
        @NotNull(message = "사진 아이디는 필수입니다.")
        Long mapImageId) {
}
