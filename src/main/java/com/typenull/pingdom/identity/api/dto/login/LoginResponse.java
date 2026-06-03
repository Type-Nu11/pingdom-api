package com.typenull.pingdom.identity.api.dto.login;

import io.swagger.v3.oas.annotations.media.Schema;

// 로그인 성공 응답 DTO
@Schema(description = "로그인 성공 응답")
public record LoginResponse(
        @Schema(description = "사용자 ID", example = "1")
        Long id,
        @Schema(description = "사용자 아이디", example = "pingdom_user")
        String username,
        @Schema(description = "사용자 이메일", example = "pingdom@example.com")
        String email,
        @Schema(description = "출생 연도", example = "1998")
        Integer birthYear,
        @Schema(description = "프로필 이미지 URL", example = "https://cdn.pingdom.com/profiles/user1.png", nullable = true)
        String profileImageUrl,
        @Schema(description = "언어", example = "ko")
        String language,
        @Schema(description = "국가", example = "KR")
        String country,
        @Schema(description = "로그인 결과 메시지", example = "로그인에 성공했습니다.")
        String message,
        @Schema(description = "인증에 사용할 Access Token", example = "eyJhbGciOiJIUzI1NiJ9.access.token")
        String accessToken,
        @Schema(description = "토큰 재발급에 사용할 Refresh Token", example = "eyJhbGciOiJIUzI1NiJ9.refresh.token")
        String refreshToken
) {
    // 토큰 미포함 호출 호환 생성자
    public LoginResponse(Long id, String username, String email, Integer birthYear, String profileImageUrl, String language, String country, String message) {
        this(id, username, email, birthYear, profileImageUrl, language, country, message, null, null);
    }
}
