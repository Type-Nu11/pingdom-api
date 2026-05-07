package com.typenull.pingdom.domain.users.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "아이디 변경 요청 정보")
public record ChangeUsernameRequest(
        @NotBlank(message = "새 이름을 입력해주세요.")
        @Size(min = 4, max = 50, message = "아이디는 4자 이상 50자 이하여야 합니다.")
        @Schema(description = "변경할 새 아이디", example = "new_pingdom_user")
        String newUsername
) {
}
