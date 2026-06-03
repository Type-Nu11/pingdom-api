package com.typenull.pingdom.engagement.api.dto;

import jakarta.validation.constraints.NotNull;

public record MapImageLikeRequest(
        @NotNull(message = "사진 아이디는 필수입니다.")
        Long mapImageId) {
}
