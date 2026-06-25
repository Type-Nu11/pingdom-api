package com.typenull.pingdom.identity.api.dto.oauth;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "OAuth 계정 연결 해제 요청")
public record OAuthAccountDisconnectRequest(
        @Schema(
                description = "마지막 OAuth 계정을 해제할 때 확인할 현재 비밀번호",
                example = "password123",
                nullable = true
        )
        String currentPassword
) {
}
