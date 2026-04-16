package com.typenull.pingdom.domain.users.dto;

import com.typenull.pingdom.domain.users.exception.UsersErrorCode;
import com.typenull.pingdom.domain.users.exception.UsersException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "현재 비밀번호를 입력해주세요.")
        String currentPassword,

        @NotBlank(message = "새 비밀번호를 입력해주세요.")
        @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
        String newPassword,

        @NotBlank(message = "다시 한번 입력해주세요.")
        @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
        String confirmPassword
) {
    public void validatePassword() {
        if (!newPassword.equals(confirmPassword)) {
            throw new UsersException(UsersErrorCode.PASSWORD_MISMATCH);
        }
    }
}