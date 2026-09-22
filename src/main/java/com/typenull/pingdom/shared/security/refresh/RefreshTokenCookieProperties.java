package com.typenull.pingdom.shared.security.refresh;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** refresh 쿠키의 보안·배포 범위를 바인딩한다. domain은 생략 가능하며 SameSite는 Strict 또는 Lax만 허용한다. */
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
