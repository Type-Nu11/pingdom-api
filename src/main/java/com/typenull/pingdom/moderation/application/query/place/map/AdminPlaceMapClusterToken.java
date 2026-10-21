package com.typenull.pingdom.moderation.application.query.place.map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import com.typenull.pingdom.shared.security.jwt.JwtProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Access/Refresh JWT와 구별되는 목적별 HMAC 토큰. 저장소 없이 다중 인스턴스에서 검증한다. */
@Component
public class AdminPlaceMapClusterToken {
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private final ObjectMapper mapper;
    private final byte[] key;
    private final Clock clock;

    @Autowired
    public AdminPlaceMapClusterToken(ObjectMapper mapper, JwtProperties properties) {
        this(mapper, properties, Clock.systemUTC());
    }

    AdminPlaceMapClusterToken(ObjectMapper mapper, JwtProperties properties, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
        this.key = properties.secret().getBytes(StandardCharsets.UTF_8);
    }

    public String issue(AdminPlaceMapCondition condition, double cellSize, long x, long y,
                        boolean unknownOnly, String fingerprint) {
        var payload = new Payload(1, clock.instant().getEpochSecond() + 300,
                condition, cellSize, x, y, unknownOnly, fingerprint);
        try {
            String body = ENCODER.encodeToString(mapper.writeValueAsBytes(payload));
            return body + "." + ENCODER.encodeToString(sign(body));
        } catch (java.io.IOException e) {
            throw new IllegalStateException("관리자 지도 그룹 토큰 직렬화 실패", e);
        }
    }

    /** 표시 키는 필터·고정 격자만 사용한다. 화면 이동, 토큰 발급 시각, 멤버 수는 포함하지 않는다. */
    public String displayKey(AdminPlaceMapCondition c, double cell, long x, long y, boolean unknownOnly) {
        try {
            String scope = c.keyword().toLowerCase(java.util.Locale.ROOT) + "\n" + c.category() + "\n"
                    + c.discoveryStatus() + "\n" + c.operatingStatus() + "\n" + unknownOnly;
            String hash = java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(scope.getBytes(StandardCharsets.UTF_8)));
            return "v1:" + hash + ":" + Double.toHexString(cell) + ":" + x + ":" + y;
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public Payload read(String token) {
        try {
            if (token == null || token.length() > 4096) throw invalid();
            String[] parts = token.split("\\.", -1);
            if (parts.length != 2 || !MessageDigest.isEqual(sign(parts[0]), DECODER.decode(parts[1]))) throw invalid();
            Payload p = mapper.readValue(DECODER.decode(parts[0]), Payload.class);
            if (p.version() != 1 || p.expiresAt() <= clock.instant().getEpochSecond()
                    || p.condition() == null || !Double.isFinite(p.cellSize()) || p.cellSize() <= 0
                    || p.fingerprint() == null) throw invalid();
            p.condition().validate();
            return p;
        } catch (java.io.IOException | IllegalArgumentException | AdminException e) {
            throw invalid();
        }
    }

    private byte[] sign(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(("admin-place-map:v1:" + body).getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("관리자 지도 그룹 토큰 서명 실패", e);
        }
    }

    private AdminException invalid() {
        return new AdminException(AdminErrorCode.ADMIN_PLACE_MAP_CLUSTER_INVALID);
    }

    public record Payload(int version, long expiresAt, AdminPlaceMapCondition condition,
                          double cellSize, long x, long y, boolean unknownOnly, String fingerprint) {}
}
