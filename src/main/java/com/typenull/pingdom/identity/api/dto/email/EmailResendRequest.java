package com.typenull.pingdom.identity.api.dto.email;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "이메일 인증 메일 재발송 요청 정보")
public record EmailResendRequest(
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Schema(description = "인증 메일을 다시 받을 이메일 주소", example = "pingdom@example.com")
        String email
) {
}
