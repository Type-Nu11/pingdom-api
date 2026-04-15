package com.typenull.pingdom.domain.auth.dto.email;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

// 이메일 인증 요청 본문 DTO
public record EmailVerifyRequest(
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        String email,

        @NotBlank(message = "인증 코드는 필수입니다.")
        String code
) {
    // 기존 단일 이메일 요청 호환 생성자
    public EmailVerifyRequest(String email) {
        this(email, "TEMP-CODE");
    }
}
