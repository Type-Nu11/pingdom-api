package com.typenull.pingdom.identity.application.service;

public record TokenRefreshResult(
        String accessToken,
        String refreshToken
) {
}
