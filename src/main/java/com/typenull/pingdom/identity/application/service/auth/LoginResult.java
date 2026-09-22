package com.typenull.pingdom.identity.application.service.auth;

import com.typenull.pingdom.identity.api.dto.login.LoginResponse;

/**
 * 로그인 응답 본문과 쿠키에 사용할 refresh token을 API 계층에 함께 전달.
 */
public record LoginResult(
        LoginResponse response,
        String refreshToken
) {
}
