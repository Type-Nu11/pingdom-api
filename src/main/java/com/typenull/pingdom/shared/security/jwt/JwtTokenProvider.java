package com.typenull.pingdom.shared.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
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
    public String generateAccessToken(Long userId, String username, String role) {
        return buildToken(userId, username, role, jwtProperties.accessTokenExpirationSeconds(), "access");
    }

    // Refresh Token 생성 메서드
    public String generateRefreshToken(Long userId) {
        return buildToken(userId, null, null, jwtProperties.refreshTokenExpirationSeconds(), "refresh");
    }

    // Refresh Token 유효성 검사 메서드
    public boolean validateRefreshToken(String refreshToken) {
        return parseRefreshToken(refreshToken).status() == TokenStatus.VALID;
    }

    public RefreshTokenParseResult parseRefreshToken(String refreshToken) {
        try {
            Claims claims = parseClaims(refreshToken);
            if (!"refresh".equals(claims.get("type", String.class))) {
                return new RefreshTokenParseResult(TokenStatus.INVALID, null);
            }

            Long userId = Long.valueOf(claims.getSubject());
            return new RefreshTokenParseResult(TokenStatus.VALID, userId);
        } catch (ExpiredJwtException exception) {
            return new RefreshTokenParseResult(TokenStatus.EXPIRED, null);
        } catch (JwtException | IllegalArgumentException exception) {
            return new RefreshTokenParseResult(TokenStatus.INVALID, null);
        }
    }

    // Access Token 유효성 검사 메서드
    public boolean validateAccessToken(String accessToken) {
        return validateAccessTokenStatus(accessToken) == TokenStatus.VALID;
    }

    public AccessTokenParseResult parseAccessToken(String accessToken) {
        try {
            Claims claims = parseClaims(accessToken);
            if (!"access".equals(claims.get("type", String.class))) {
                return new AccessTokenParseResult(TokenStatus.INVALID, null);
            }

            Long userId = Long.valueOf(claims.getSubject());
            String username = claims.get("username", String.class);
            String role = claims.get("role", String.class);

            return new AccessTokenParseResult(TokenStatus.VALID, new AccessTokenPayload(userId, username, role));
        } catch (ExpiredJwtException exception) {
            return new AccessTokenParseResult(TokenStatus.EXPIRED, null);
        } catch (JwtException | IllegalArgumentException exception) {
            return new AccessTokenParseResult(TokenStatus.INVALID, null);
        }
    }

    public TokenStatus validateAccessTokenStatus(String accessToken) {
        try {
            Claims claims = parseClaims(accessToken);
            return "access".equals(claims.get("type", String.class)) ? TokenStatus.VALID : TokenStatus.INVALID;
        } catch (ExpiredJwtException exception) {
            return TokenStatus.EXPIRED;
        } catch (JwtException | IllegalArgumentException exception) {
            return TokenStatus.INVALID;
        }
    }

    // Refresh Token 사용자 ID 추출 메서드
    public Long getUserIdFromRefreshToken(String refreshToken) {
        RefreshTokenParseResult parsed = parseRefreshToken(refreshToken);
        if (parsed.status() != TokenStatus.VALID || parsed.userId() == null) {
            throw new IllegalArgumentException("유효한 리프레시 토큰이 아닙니다.");
        }

        return parsed.userId();
    }

    // Access Token 사용자 ID 추출 메서드
    public Long getUserIdFromAccessToken(String accessToken) {
        Claims claims = parseClaims(accessToken);

        if (!"access".equals(claims.get("type", String.class))) {
            throw new IllegalArgumentException("액세스 토큰 타입이 아닙니다.");
        }

        return Long.valueOf(claims.getSubject());
    }

    // Access Token 사용자명 추출 메서드
    public String getUsernameFromAccessToken(String accessToken) {
        Claims claims = parseClaims(accessToken);

        if (!"access".equals(claims.get("type", String.class))) {
            throw new IllegalArgumentException("액세스 토큰 타입이 아닙니다.");
        }

        return claims.get("username", String.class);
    }

    public String getRoleFromAccessToken(String accessToken) {
        Claims claims = parseClaims(accessToken);

        if (!"access".equals(claims.get("type", String.class))) {
            throw new IllegalArgumentException("액세스 토큰 타입이 아닙니다.");
        }

        return claims.get("role", String.class);
    }

    // JWT 공통 생성 메서드
    private String buildToken(Long userId, String username, String role, long expirationSeconds, String tokenType) {
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
        if (role != null) {
            builder.claim("role", role);
        }
        if ("refresh".equals(tokenType)) {
            builder.id(UUID.randomUUID().toString());
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

    public enum TokenStatus {
        VALID,
        EXPIRED,
        INVALID
    }

    public record AccessTokenPayload(Long userId, String username, String role) {
    }

    public record AccessTokenParseResult(TokenStatus status, AccessTokenPayload payload) {
    }

    public record RefreshTokenParseResult(TokenStatus status, Long userId) {
    }
}
