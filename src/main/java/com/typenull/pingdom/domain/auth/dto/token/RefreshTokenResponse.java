package com.typenull.pingdom.domain.auth.dto.token;

// Refresh Token 재발급 응답 DTO
public record RefreshTokenResponse(
        String accessToken,
        String refreshToken
) {
}
