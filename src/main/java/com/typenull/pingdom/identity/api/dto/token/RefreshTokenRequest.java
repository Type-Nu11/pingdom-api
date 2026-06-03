package com.typenull.pingdom.identity.api.dto.token;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

// Refresh Token 재발급 요청 DTO
@Schema(description = "토큰 재발급 요청 정보")
public record RefreshTokenRequest(
        @NotBlank(message = "리프레시 토큰은 필수입니다.")
        @Schema(description = "재발급 검증에 사용할 Refresh Token", example = "eyJhbGciOiJIUzI1NiJ9.refresh.token")
        String refreshToken
) {
}
