package com.typenull.pingdom.domain.map.dto;

import jakarta.validation.constraints.NotBlank;

public record MapImageLikeRequest(
        @NotBlank(message = "사잔 아이디는 필수입니다.")
        Long mapImageId) {
}
