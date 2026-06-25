package com.typenull.pingdom.identity.api.dto.oauth;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "OAuth 계정 상태 응답")
public record OAuthAccountResponse(
        @Schema(description = "OAuth Provider", example = "GOOGLE")
        String provider,

        @Schema(description = "연결 여부", example = "false")
        boolean linked,

        @Schema(description = "처리 결과 메시지", example = "Google 계정 연결을 해제했습니다.")
        String message
) {
}
