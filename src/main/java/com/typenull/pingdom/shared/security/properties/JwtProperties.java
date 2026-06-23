package com.typenull.pingdom.shared.security.properties;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

// JWT 발급 설정 프로퍼티
@Validated
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        @NotBlank(message = "JWT 시크릿 값은 필수입니다.")
        @Size(min = 32, message = "JWT 시크릿은 32자 이상이어야 합니다.")
        String secret,

        @Min(value = 1, message = "Access Token 만료 시간은 1초 이상이어야 합니다.")
        long accessTokenExpirationSeconds,

        @Min(value = 1, message = "Refresh Token 만료 시간은 1초 이상이어야 합니다.")
        long refreshTokenExpirationSeconds
) {
}
