package com.typenull.pingdom.shared.security;

import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import org.junit.jupiter.api.Test;

class CookieSecurityPropertiesTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

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

    @Test
    void credentialCorsRejectsWildcardOrigin() {
        CorsProperties properties = new CorsProperties(List.of("*"));

        assertTrue(validator.validate(properties).stream()
                .anyMatch(violation -> violation.getMessage().contains("와일드카드 Origin")));
    }
}
