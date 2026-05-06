package com.typenull.pingdom.global.config.security;

// JWT 인증 완료 사용자 정보 전달 record
public record JwtAuthenticatedUser(
        Long userId,
        String username
) {
}
