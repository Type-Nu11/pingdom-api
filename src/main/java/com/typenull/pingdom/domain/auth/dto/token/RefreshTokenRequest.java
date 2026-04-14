package com.typenull.pingdom.domain.auth.dto.token;

import jakarta.validation.constraints.NotBlank;

// Refresh Token 재발급 요청 DTO
public record RefreshTokenRequest(
        @NotBlank(message = "리프레시 토큰은 필수입니다.")
        String refreshToken
) {
}
