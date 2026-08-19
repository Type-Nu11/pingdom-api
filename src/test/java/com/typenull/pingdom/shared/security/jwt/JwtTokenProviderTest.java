package com.typenull.pingdom.shared.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties(
                "test-jwt-secret-key-with-at-least-32-characters",
                3600,
                1209600
        );
        jwtTokenProvider = new JwtTokenProvider(properties);
    }

    @Test
    void accessTokenCannotBeUsedAsRefreshToken() {
        String accessToken = jwtTokenProvider.generateAccessToken(1L, "tester", "USER");

        assertThat(jwtTokenProvider.parseRefreshToken(accessToken).status())
                .isEqualTo(JwtTokenProvider.TokenStatus.INVALID);
    }

    @Test
    void refreshTokenCannotBeUsedAsAccessToken() {
        String refreshToken = jwtTokenProvider.generateRefreshToken(1L);

        assertThat(jwtTokenProvider.parseAccessToken(refreshToken).status())
                .isEqualTo(JwtTokenProvider.TokenStatus.INVALID);
    }
}
