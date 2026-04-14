package com.typenull.pingdom.domain.auth.dto.login;

// 로그인 성공 응답 DTO
public record LoginResponse(
        Long id,
        String username,
        String name,
        String message,
        String accessToken,
        String refreshToken
) {
    // 기존 응답 호출 호환 생성자
    public LoginResponse(Long id, String username, String name, String message) {
        this(id, username, name, message, null, null);
    }
}
