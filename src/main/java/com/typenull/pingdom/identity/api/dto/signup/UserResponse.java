package com.typenull.pingdom.identity.api.dto.signup;

import com.typenull.pingdom.identity.domain.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "회원가입 성공 응답")
public record UserResponse(
        @Schema(description = "생성된 사용자 ID", example = "1")
        Long id,
        @Schema(description = "생성된 사용자 아이디", example = "pingdom_user")
        String username,
        @Schema(description = "생성된 사용자 이메일", example = "pingdom@example.com")
        String email,
        @Schema(description = "출생 연도", example = "1998")
        Integer birthYear,
        @Schema(description = "프로필 이미지 URL", example = "https://cdn.pingdom.com/profiles/user1.png", nullable = true)
        String profileImageUrl,
        @Schema(description = "언어", example = "ko")
        String language,
        @Schema(description = "국가", example = "KR")
        String country,
        @Schema(description = "생성된 사용자 역할", example = "USER")
        UserRole role
) {
}
