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

        @NotBlank(message = "이름은 필수입니다.")
        @Size(max = 100, message = "이름은 100자 이하여야 합니다.")
        @Schema(description = "사용자 이름", example = "홍길동")
        String name,

        // 이메일 인증 연계용 메일 주소
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Schema(description = "이메일 인증을 받을 메일 주소. 선택 입력입니다.", example = "pingdom@example.com", nullable = true)
        String email,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
        @Schema(description = "로그인 비밀번호", example = "securePass123!")
        String password
) {
    // 기존 3개 필드 호출 호환 생성자
    public SignupRequest(String username, String name, String password) {
        this(username, name, null, password);
    }
}
