package com.typenull.pingdom.domain.auth.security;

import com.typenull.pingdom.domain.auth.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

// JWT Access Token, Refresh Token 생성 클래스
@Component
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;
    private final SecretKey secretKey;

    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.secretKey = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
    }

    // Access Token 생성 메서드
    public String generateAccessToken(Long userId, String username) {
        return buildToken(userId, username, jwtProperties.accessTokenExpirationSeconds(), "access");
    }

    // Refresh Token 생성 메서드
    public String generateRefreshToken(Long userId) {
        return buildToken(userId, null, jwtProperties.refreshTokenExpirationSeconds(), "refresh");
    }

    // Refresh Token 유효성 검사 메서드
    public boolean validateRefreshToken(String refreshToken) {
        try {
            Claims claims = parseClaims(refreshToken);
            return "refresh".equals(claims.get("type", String.class));
        } catch (JwtException | IllegalArgumentException exception) {
            return false;
        }
    }

    // Refresh Token 사용자 ID 추출 메서드
    public Long getUserIdFromRefreshToken(String refreshToken) {
        Claims claims = parseClaims(refreshToken);

        if (!"refresh".equals(claims.get("type", String.class))) {
            throw new IllegalArgumentException("리프레시 토큰 타입이 아닙니다.");
        }

        return Long.valueOf(claims.getSubject());
    }

    // JWT 공통 생성 메서드
    private String buildToken(Long userId, String username, long expirationSeconds, String tokenType) {
        Instant now = Instant.now();
        Instant expiration = now.plusSeconds(expirationSeconds);

        var builder = Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("type", tokenType)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(secretKey);

        if (username != null) {
            builder.claim("username", username);
        }

        return builder.compact();
    }

    // JWT Claims 파싱 메서드
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
