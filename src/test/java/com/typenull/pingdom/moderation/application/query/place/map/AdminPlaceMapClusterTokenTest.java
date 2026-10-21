package com.typenull.pingdom.moderation.application.query.place.map;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import com.typenull.pingdom.shared.security.jwt.JwtProperties;
import com.typenull.pingdom.place.domain.place.discovery.PlaceDiscoveryStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class AdminPlaceMapClusterTokenTest {
    private final JwtProperties properties = new JwtProperties("s".repeat(64), 60, 600);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-21T00:00:00Z"), ZoneOffset.UTC);
    private final AdminPlaceMapCondition condition = new AdminPlaceMapCondition(128, 35, 129, 36,
            "카페", "CAFE", PlaceDiscoveryStatus.HIDDEN, null);

    @Test
    void signedTokenPreservesOriginalFilterViewportAndResolution() {
        var tokens = new AdminPlaceMapClusterToken(new ObjectMapper(), properties, clock);
        var payload = tokens.read(tokens.issue(condition, 100, 13, 24, true, "fingerprint"));
        assertThat(payload.condition()).isEqualTo(condition);
        assertThat(payload.cellSize()).isEqualTo(100);
        assertThat(payload.unknownOnly()).isTrue();
        assertThat(payload.expiresAt()).isEqualTo(clock.instant().getEpochSecond() + 300);
    }

    @Test
    void rejectsTamperingExpiredAndWrongPurposeTokens() {
        var tokens = new AdminPlaceMapClusterToken(new ObjectMapper(), properties, clock);
        String token = tokens.issue(condition, 100, 13, 24, false, "fingerprint");
        assertThatThrownBy(() -> tokens.read("a" + token)).isInstanceOf(AdminException.class);
        assertThatThrownBy(() -> tokens.read("a.b.c")).isInstanceOf(AdminException.class);
        assertThatThrownBy(() -> tokens.read("x".repeat(4097))).isInstanceOf(AdminException.class);
        var expired = new AdminPlaceMapClusterToken(new ObjectMapper(), properties, Clock.offset(clock, java.time.Duration.ofSeconds(300)));
        assertThatThrownBy(() -> expired.read(token)).isInstanceOf(AdminException.class);
    }
    @Test
    void displayKeyStaysStableAcrossPanAndTokenRenewalButChangesWithResolutionOrFilter() {
        var tokens = new AdminPlaceMapClusterToken(new ObjectMapper(), properties, clock);
        var shifted = new AdminPlaceMapCondition(127,34,130,37,"카페","CAFE",PlaceDiscoveryStatus.HIDDEN,null);
        String key = tokens.displayKey(condition,100,13,24,false);
        assertThat(tokens.displayKey(shifted,100,13,24,false)).isEqualTo(key);
        assertThat(tokens.displayKey(condition,200,13,24,false)).isNotEqualTo(key);
        assertThat(tokens.displayKey(condition,100,13,24,true)).isNotEqualTo(key);
    }

}
