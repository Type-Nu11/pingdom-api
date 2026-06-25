package com.typenull.pingdom.identity.api.dto.oauth;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "OAuth 계정 연결 시작 응답")
public record OAuthAccountLinkStartResponse(
        @Schema(description = "OAuth Provider", example = "GOOGLE")
        String provider,

        @Schema(description = "브라우저가 이동할 OAuth 인증 시작 URL", example = "/oauth2/authorization/google")
        String authorizationUrl,

        @Schema(description = "처리 결과 메시지", example = "Google 계정 연결을 시작합니다.")
        String message
) {
}
