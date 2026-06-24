package com.typenull.pingdom.identity.api.dto.passwordreset;

import com.typenull.pingdom.identity.domain.exception.AuthErrorCode;
import com.typenull.pingdom.identity.domain.exception.AuthException;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "비밀번호 재설정 완료 요청 정보")
public record PasswordResetConfirmRequest(
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Schema(description = "비밀번호를 재설정할 이메일 주소", example = "pingdom@example.com")
        String email,

        @NotBlank(message = "재설정 토큰은 필수입니다.")
        @Schema(description = "메일로 발급된 비밀번호 재설정 토큰")
        String token,

        @NotBlank(message = "새 비밀번호를 입력해주세요.")
        @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
        @Schema(description = "변경할 새 비밀번호", example = "newSecurePass123!")
        String newPassword,

        @NotBlank(message = "다시 한번 입력해주세요.")
        @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
        @Schema(description = "새 비밀번호 확인 입력", example = "newSecurePass123!")
        String confirmPassword
) {

    public void validatePassword() {
        if (!newPassword.equals(confirmPassword)) {
            throw new AuthException(AuthErrorCode.PASSWORD_MISMATCH);
        }
    }
}
