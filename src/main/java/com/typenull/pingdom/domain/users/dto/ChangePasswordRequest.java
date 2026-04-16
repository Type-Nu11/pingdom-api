package com.typenull.pingdom.domain.users.dto;

import com.typenull.pingdom.domain.auth.exception.AuthErrorCode;
import com.typenull.pingdom.domain.auth.exception.AuthException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChangePasswordRequest {
    @NotBlank(message = "현재 비밀번호를 입력해주세요.")
    private String currentPassword;

    @NotBlank(message = "새 비밀번호를 입력해주세요.")
    @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
    private String newPassword;

    @NotBlank(message = "다시 한번 입력해주세요.")
    @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
    private String confirmPassword;

    public void validatePassword() {
        if (!newPassword.equals(confirmPassword)) {
            throw new AuthException(AuthErrorCode.INVALID_CREDENTIALS);
        }
    }
}


