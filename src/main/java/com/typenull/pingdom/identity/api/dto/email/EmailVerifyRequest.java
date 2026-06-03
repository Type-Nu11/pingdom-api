package com.typenull.pingdom.identity.api.dto.email;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

// 이메일 인증 요청 본문 DTO
@Schema(description = "이메일 인증 요청 정보")
public record EmailVerifyRequest(
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Schema(description = "인증 대상 이메일 주소", example = "pingdom@example.com")
        String email,

        @NotBlank(message = "인증 코드는 필수입니다.")
        @Schema(description = "이메일로 전달된 인증 코드", example = "123456")
        String code
) {
    // 기존 단일 이메일 요청 호환 생성자
    public EmailVerifyRequest(String email) {
        this(email, "TEMP-CODE");
    }
}
