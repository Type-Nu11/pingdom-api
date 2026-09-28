package com.typenull.pingdom.shared.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

    private static final String JWT_SECRET = "test-jwt-secret-key-with-at-least-sixty-four-characters-for-hs512-signing";

    private JwtTokenProvider jwtTokenProvider;

    /**
     * 테스트 전용 서명 키와 액세스/리프레시 유효기간으로 JWT 공급자를 구성.
     */
    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties(
                JWT_SECRET,
                3600,
                1209600
        );
        jwtTokenProvider = new JwtTokenProvider(properties);
    }

    /**
     * 정상 발급 액세스 JWT라도 리프레시 파서에서 INVALID로 판정해 토큰 용도 혼용을 방지하는지 검증.
     */
    @Test
    void rejectsAccessTokenAsRefresh() {
        String accessToken = jwtTokenProvider.generateAccessToken(1L, "tester", "USER");

        assertThat(jwtTokenProvider.parseRefreshToken(accessToken).status())
                .isEqualTo(JwtTokenProvider.TokenStatus.INVALID);
    }

    /**
     * 정상 발급 리프레시 JWT라도 액세스 파서에서 INVALID로 판정해 API 인증에 재사용하지 못하는지 검증.
     */
    @Test
    void rejectsRefreshTokenAsAccess() {
        String refreshToken = jwtTokenProvider.generateRefreshToken(1L);

        assertThat(jwtTokenProvider.parseAccessToken(refreshToken).status())
                .isEqualTo(JwtTokenProvider.TokenStatus.INVALID);
    }

    /**
     * Gateway가 요구하는 JWT protected header의 typ과 HS512 서명 알고리즘을 access token에 포함하는지 검증.
     */
    @Test
    void generatesAccessTokenWithJwtTypeHeaderAndHs512Algorithm() {
        String accessToken = jwtTokenProvider.generateAccessToken(1L, "tester", "USER");

        var header = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8)))
                .build()
                .parseSignedClaims(accessToken)
                .getHeader();

        assertThat(header.getType()).isEqualTo("JWT");
        assertThat(header.getAlgorithm()).isEqualTo("HS512");
    }
}
