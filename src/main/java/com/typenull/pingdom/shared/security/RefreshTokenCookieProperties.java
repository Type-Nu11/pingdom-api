package com.typenull.pingdom.shared.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "auth.refresh-cookie")
public record RefreshTokenCookieProperties(
        @NotBlank(message = "Refresh Token Cookie 이름은 필수입니다.")
        String name,
        boolean secure,
        @NotBlank(message = "Refresh Token Cookie SameSite 값은 필수입니다.")
        @Pattern(regexp = "(?i)Strict|Lax", message = "Refresh Token Cookie SameSite 값은 Strict 또는 Lax여야 합니다.")
        String sameSite,
        String domain
) {
}
