package com.typenull.pingdom.shared.security.jwt;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** HS512 서명 키 문자열과 토큰 수명(초). 키는 UTF-8 바이트로 변환되며 최소 64자 검증을 거침. */
@Validated
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        @NotBlank(message = "JWT 시크릿 값은 필수입니다.")
        @Size(min = 64, message = "JWT 시크릿은 HS512 서명을 위해 64자 이상이어야 합니다.")
        String secret,

        @Min(value = 1, message = "Access Token 만료 시간은 1초 이상이어야 합니다.")
        long accessTokenExpirationSeconds,

        @Min(value = 1, message = "Refresh Token 만료 시간은 1초 이상이어야 합니다.")
        long refreshTokenExpirationSeconds
) {
}
