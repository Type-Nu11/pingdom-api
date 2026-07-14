package com.typenull.pingdom.identity.application.service.auth;

public record TokenRefreshResult(
        String accessToken,
        String refreshToken
) {
}
