package com.typenull.pingdom.domain.auth.dto.login;

import io.swagger.v3.oas.annotations.media.Schema;

// 로그인 성공 응답 DTO
@Schema(description = "로그인 성공 응답")
public record LoginResponse(
        @Schema(description = "사용자 ID", example = "1")
        Long id,
        @Schema(description = "사용자 아이디", example = "pingdom_user")
        String username,
        @Schema(description = "사용자 이름", example = "홍길동")
        String name,
        @Schema(description = "로그인 결과 메시지", example = "로그인에 성공했습니다.")
        String message,
        @Schema(description = "인증에 사용할 Access Token", example = "eyJhbGciOiJIUzI1NiJ9.access.token")
        String accessToken,
        @Schema(description = "토큰 재발급에 사용할 Refresh Token", example = "eyJhbGciOiJIUzI1NiJ9.refresh.token")
        String refreshToken
) {
    // 기존 응답 호출 호환 생성자
    public LoginResponse(Long id, String username, String name, String message) {
        this(id, username, name, message, null, null);
    }
}
