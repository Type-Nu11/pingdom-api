package com.typenull.pingdom.identity.infrastructure.oauth;

import com.typenull.pingdom.identity.domain.exception.AuthErrorCode;
import com.typenull.pingdom.identity.domain.exception.AuthException;
import com.typenull.pingdom.shared.security.jwt.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class OAuth2LinkTokenService {

    private static final String TOKEN_TYPE = "oauth_link";
    private static final long EXPIRATION_SECONDS = 300L;

    private final SecretKey secretKey;
    private final Clock clock;

    public OAuth2LinkTokenService(JwtProperties jwtProperties, Clock clock) {
        this.secretKey = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
        this.clock = clock;
    }

    public String generate(Long userId) {
        Instant now = clock.instant();
        Instant expiration = now.plusSeconds(EXPIRATION_SECONDS);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("type", TOKEN_TYPE)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(secretKey)
                .compact();
    }

    public Long parseUserId(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            if (!TOKEN_TYPE.equals(claims.get("type", String.class))) {
                throw new AuthException(AuthErrorCode.OAUTH_LINK_TOKEN_INVALID);
            }
            return Long.valueOf(claims.getSubject());
        } catch (JwtException | IllegalArgumentException exception) {
            throw new AuthException(AuthErrorCode.OAUTH_LINK_TOKEN_INVALID);
        }
    }
}
