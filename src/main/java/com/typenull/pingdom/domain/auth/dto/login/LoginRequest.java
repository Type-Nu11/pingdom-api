package com.typenull.pingdom.domain.auth.dto.login;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "아이디는 필수입니다.")
        @Size(min = 4, max = 50, message = "아이디는 4자 이상 50자 이하여야 합니다.")
        String username,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
        String password
) {
}
