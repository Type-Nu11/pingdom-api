package com.typenull.pingdom.domain.auth.dto.login;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "로그인 요청 정보")
public record LoginRequest(
        @NotBlank(message = "아이디는 필수입니다.")
        @Size(min = 4, max = 50, message = "아이디는 4자 이상 50자 이하여야 합니다.")
        @Schema(description = "로그인에 사용할 아이디", example = "pingdom_user")
        String username,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
        @Schema(description = "로그인 비밀번호", example = "securePass123!")
        String password
) {
}
