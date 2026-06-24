package com.typenull.pingdom.domain.users.dto;

import com.typenull.pingdom.domain.users.exception.UsersErrorCode;
import com.typenull.pingdom.domain.users.exception.UsersException;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "비밀번호 변경 요청 정보")
public record ChangePasswordRequest(
        @NotBlank(message = "현재 비밀번호를 입력해주세요.")
        @Schema(description = "현재 비밀번호", example = "currentPass123!")
        String currentPassword,

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
            throw new UsersException(UsersErrorCode.PASSWORD_MISMATCH);
        }
    }
}
