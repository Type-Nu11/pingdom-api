package com.typenull.pingdom.domain.auth.dto.token;

import io.swagger.v3.oas.annotations.media.Schema;

// Refresh Token 재발급 응답 DTO
@Schema(description = "토큰 재발급 응답")
public record RefreshTokenResponse(
        @Schema(description = "새로 발급된 Access Token", example = "eyJhbGciOiJIUzI1NiJ9.new.access.token")
        String accessToken,
        @Schema(description = "새로 발급된 Refresh Token", example = "eyJhbGciOiJIUzI1NiJ9.new.refresh.token")
        String refreshToken
) {
}
