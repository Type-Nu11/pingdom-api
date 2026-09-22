package com.typenull.pingdom.shared.security.config;

import com.typenull.pingdom.shared.security.cors.CorsProperties;
import com.typenull.pingdom.shared.security.refresh.RefreshTokenCookieProperties;

import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import org.junit.jupiter.api.Test;

class CookieSecurityPropertiesTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    /**
     * 리프레시 쿠키에 SameSite=None을 지정하면 Strict 또는 Lax를 요구하는 설정 검증 오류가 발생하는지 확인한다.
     */
    @Test
    void refreshTokenCookieRejectsSameSiteNone() {
        RefreshTokenCookieProperties properties = new RefreshTokenCookieProperties(
                "PINGDOM_REFRESH_TOKEN",
                true,
                "None",
                null
        );

        assertTrue(validator.validate(properties).stream()
                .anyMatch(violation -> violation.getMessage().contains("Strict 또는 Lax")));
    }

    /**
     * CORS Origin 목록의 와일드카드가 검증 오류로 거절되는지 확인한다.
     */
    @Test
    void credentialCorsRejectsWildcardOrigin() {
        CorsProperties properties = new CorsProperties(List.of("*"));

        assertTrue(validator.validate(properties).stream()
                .anyMatch(violation -> violation.getMessage().contains("와일드카드 Origin")));
    }
}
