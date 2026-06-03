package com.typenull.pingdom.identity.api.dto.profile;

import com.typenull.pingdom.identity.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder
@Schema(description = "내 정보 조회 응답")
public record MyPageResponse(
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
        String country){

    public static MyPageResponse from(User user) {
        return MyPageResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .birthYear(user.getBirthYear())
                .profileImageUrl(user.getProfileImageUrl())
                .language(user.getLanguage())
                .country(user.getCountry())
                .build();
    }
}
