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

/** HMAC 서명·만료·토큰 종류를 확인하고 JWT 발급 및 claim 추출을 담당한다.
 * 사용자 계정 상태나 refresh token의 저장소 폐기 여부는 호출자가 별도로 확인한다. */
@Component
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;
    private final SecretKey secretKey;

    public JwtTokenProvider(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.secretKey = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
    }

    // Access Token 생성 메서드
    /** 사용자 식별자와 권한을 담은 access token을 발급합니다. */
    public String generateAccessToken(Long userId, String username, String role) {
        return buildToken(userId, username, role, jwtProperties.accessTokenExpirationSeconds(), "access");
    }

    // Refresh Token 생성 메서드
    /** 사용자 식별자와 매번 새로운 jti를 담은 refresh token을 발급해 같은 시점의 토큰도 구분한다. */
    public String generateRefreshToken(Long userId) {
        return buildToken(userId, null, null, jwtProperties.refreshTokenExpirationSeconds(), "refresh");
    }

    // Refresh Token 유효성 검사 메서드
    public boolean validateRefreshToken(String refreshToken) {
        return parseRefreshToken(refreshToken).status() == TokenStatus.VALID;
    }

    /** refresh 종류와 숫자 사용자 식별자를 해석한다. 만료와 그 외 파싱 실패를 구분하며 실패 결과의 userId는 null이다. */
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

    /** 서명·만료·access 종류와 숫자 subject를 확인한다. username·role은 누락될 수 있으며 이 메서드에서 필수 검증하지 않는다. */
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

    /** 서명·만료 및 access 종류만 판정한다. payload를 읽는 parseAccessToken과 달리 subject를 숫자로 변환하지 않는다. */
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

    /** 성공 시 payload, 실패 시 null을 담는다. 호출자는 상태 확인 후 payload에 접근해야 한다. */
    public record AccessTokenParseResult(TokenStatus status, AccessTokenPayload payload) {
    }

    /** 성공 시 사용자 식별자를 담고 만료·무효 토큰에는 null을 반환한다. */
    public record RefreshTokenParseResult(TokenStatus status, Long userId) {
    }
}
