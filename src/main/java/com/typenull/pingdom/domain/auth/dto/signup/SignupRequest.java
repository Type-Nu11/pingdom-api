package com.typenull.pingdom.domain.auth.dto.signup;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "회원가입 요청 정보")
public record SignupRequest(
        @NotBlank(message = "아이디는 필수입니다.")
        @Size(min = 4, max = 50, message = "아이디는 4자 이상 50자 이하여야 합니다.")
        @Schema(description = "로그인에 사용할 아이디", example = "pingdom_user")
        String username,

        // 이메일 인증 연계용 메일 주소
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Schema(description = "이메일 인증을 받을 메일 주소", example = "pingdom@example.com")
        String email,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
        @Schema(description = "로그인 비밀번호", example = "securePass123!")
        String password,

        @Schema(description = "출생 연도", example = "1998", nullable = true)
        Integer birthYear,

        @Schema(description = "프로필 이미지 URL", example = "https://cdn.pingdom.com/profiles/user1.png", nullable = true)
        String profileImageUrl,

        @Size(max = 20, message = "언어는 20자 이하여야 합니다.")
        @Schema(description = "언어 코드 또는 언어명", example = "ko", nullable = true)
        String language,

        @Size(max = 100, message = "국가는 100자 이하여야 합니다.")
        @Schema(description = "국가 코드 또는 국가명", example = "KR", nullable = true)
        String country
) {
    // 기존 테스트 호출 호환 생성자
    public SignupRequest(String username, String ignoredName, String email, String password) {
        this(username, email, password, null, null, null, null);
    }
}
