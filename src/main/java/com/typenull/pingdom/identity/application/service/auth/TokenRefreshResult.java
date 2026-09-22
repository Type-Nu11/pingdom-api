package com.typenull.pingdom.identity.application.service.auth;

/**
 * 세션 회전으로 새로 발급된 access token과 refresh token 쌍입니다.
 */
public record TokenRefreshResult(
        String accessToken,
        String refreshToken
) {
}
