package com.typenull.pingdom.identity.api.dto.passwordreset;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "비밀번호 재설정 토큰 발급 요청 정보")
public record PasswordResetRequest(
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Schema(description = "비밀번호 재설정 메일을 받을 이메일 주소", example = "pingdom@example.com")
        String email
) {
}
