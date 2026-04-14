package com.typenull.pingdom.domain.auth.dto.email;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

// 이메일 인증 요청 본문 DTO
public record EmailVerifyRequest(
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        String email
) {
}
