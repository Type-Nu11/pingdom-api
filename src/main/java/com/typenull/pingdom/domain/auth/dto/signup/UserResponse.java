package com.typenull.pingdom.domain.auth.dto.signup;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "회원가입 성공 응답")
public record UserResponse(
        @Schema(description = "생성된 사용자 ID", example = "1")
        Long id,
        @Schema(description = "생성된 사용자 아이디", example = "pingdom_user")
        String username,
        @Schema(description = "생성된 사용자 이름", example = "홍길동")
        String name
) {
}
