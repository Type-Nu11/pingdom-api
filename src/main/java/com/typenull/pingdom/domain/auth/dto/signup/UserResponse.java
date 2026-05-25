package com.typenull.pingdom.domain.auth.dto.signup;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "회원가입 성공 응답")
public record UserResponse(
        @Schema(description = "생성된 사용자 ID", example = "1")
        Long id,
        @Schema(description = "생성된 사용자 아이디", example = "pingdom_user")
        String username,
        @Schema(description = "생성된 사용자 이메일", example = "pingdom@example.com")
        String email,
        @Schema(description = "출생 연도", example = "1998", nullable = true)
        Integer birthYear,
        @Schema(description = "프로필 이미지 URL", example = "https://cdn.pingdom.com/profiles/user1.png", nullable = true)
        String profileImageUrl,
        @Schema(description = "언어", example = "ko", nullable = true)
        String language,
        @Schema(description = "국가", example = "KR", nullable = true)
        String country
) {
}
