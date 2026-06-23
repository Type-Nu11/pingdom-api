package com.typenull.pingdom.shared.ratelimit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.typenull.pingdom.shared.ratelimit.AbuseRateLimitProperties.EmailResendPolicy;
import com.typenull.pingdom.shared.ratelimit.AbuseRateLimitProperties.WindowPolicy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AbuseRateLimitServiceTest {

    private MutableClock clock;
    private FakeRateLimitStore store;
    private AbuseRateLimitService abuseRateLimitService;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-06-23T00:00:00Z"), ZoneOffset.UTC);
        AbuseRateLimitProperties properties = new AbuseRateLimitProperties(
                new WindowPolicy(2, Duration.ofMinutes(1)),
                new WindowPolicy(100, Duration.ofMinutes(1)),
                new WindowPolicy(2, Duration.ofMinutes(1)),
                new WindowPolicy(100, Duration.ofMinutes(1)),
                new EmailResendPolicy(
                        Duration.ofMinutes(1),
                        new WindowPolicy(5, Duration.ofDays(1)),
                        new WindowPolicy(100, Duration.ofDays(1))
                ),
                new WindowPolicy(2, Duration.ofHours(1)),
                new WindowPolicy(100, Duration.ofHours(1)),
                new WindowPolicy(2, Duration.ofMinutes(1)),
                new WindowPolicy(100, Duration.ofMinutes(1)),
                new WindowPolicy(2, Duration.ofMinutes(1)),
                new WindowPolicy(100, Duration.ofMinutes(1)),
                new WindowPolicy(1, Duration.ofHours(1)),
                new WindowPolicy(100, Duration.ofHours(1)),
                "test:rate-limit:",
                true
        );
        store = new FakeRateLimitStore(clock);
        abuseRateLimitService = new AbuseRateLimitService(properties, store);
    }

    @Test
    void loginLimitExpiresAfterWindow() {
        abuseRateLimitService.checkLogin("RateUser", "203.0.113.10");
        abuseRateLimitService.checkLogin("rateuser", "203.0.113.11");

        assertThrows(RateLimitException.class, () ->
                abuseRateLimitService.checkLogin("RATEUSER", "203.0.113.12")
        );

        clock.advance(Duration.ofMinutes(1));

        assertDoesNotThrow(() -> abuseRateLimitService.checkLogin("rateuser", "203.0.113.13"));
    }

    @Test
    void emailResendUsesMinimumIntervalPerEmail() {
        abuseRateLimitService.checkEmailResend("User@Example.com", "203.0.113.20");

        assertThrows(RateLimitException.class, () ->
                abuseRateLimitService.checkEmailResend("user@example.com", "203.0.113.21")
        );

        clock.advance(Duration.ofMinutes(1));

        assertDoesNotThrow(() -> abuseRateLimitService.checkEmailResend("user@example.com", "203.0.113.22"));
    }

    @Test
    void imageUploadLimitUsesUserWindow() {
        abuseRateLimitService.checkImageUpload(1L, "203.0.113.30");

        assertThrows(RateLimitException.class, () ->
                abuseRateLimitService.checkImageUpload(1L, "203.0.113.31")
        );

        assertDoesNotThrow(() -> abuseRateLimitService.checkImageUpload(2L, "203.0.113.32"));
    }

    @Test
    void tokenRefreshFingerprintPreservesCaseSensitiveValue() {
        abuseRateLimitService.checkTokenRefresh("Refresh.Token.A", "203.0.113.40");
        String upperTokenKey = store.lastWindowKeys().stream()
                .filter(key -> key.startsWith("token-refresh:token:"))
                .findFirst()
                .orElseThrow();

        abuseRateLimitService.checkTokenRefresh("Refresh.Token.a", "203.0.113.41");
        String lowerTokenKey = store.lastWindowKeys().stream()
                .filter(key -> key.startsWith("token-refresh:token:"))
                .findFirst()
                .orElseThrow();

        org.junit.jupiter.api.Assertions.assertNotEquals(upperTokenKey, lowerTokenKey);
    }

    private static class MutableClock extends Clock {

        private Instant instant;
        private final ZoneId zone;

        private MutableClock(Instant instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }
    }

    private static class FakeRateLimitStore implements RateLimitStore {

        private final Clock clock;
        private final Map<String, WindowState> windows = new ConcurrentHashMap<>();
        private final Map<String, CooldownState> cooldowns = new ConcurrentHashMap<>();
        private List<String> lastWindowKeys = List.of();

        private FakeRateLimitStore(Clock clock) {
            this.clock = clock;
        }

        @Override
        public void acquire(
                String message,
                Collection<RateLimitWindowRule> windowRules,
                Collection<RateLimitCooldownRule> cooldownRules
        ) {
            Instant now = Instant.now(clock);
            lastWindowKeys = windowRules.stream()
                    .map(RateLimitWindowRule::key)
                    .toList();
            for (RateLimitCooldownRule rule : cooldownRules) {
                CooldownState state = cooldowns.get(rule.key());
                if (state != null && now.isBefore(state.nextAllowedAt())) {
                    throw new RateLimitException(message);
                }
            }

            for (RateLimitWindowRule rule : windowRules) {
                WindowState state = activeWindowState(rule, now);
                if (state.count >= rule.limit()) {
                    throw new RateLimitException(message);
                }
            }

            for (RateLimitWindowRule rule : windowRules) {
                WindowState state = activeWindowState(rule, now);
                state.count++;
                windows.put(rule.key(), state);
            }

            for (RateLimitCooldownRule rule : cooldownRules) {
                cooldowns.put(rule.key(), new CooldownState(now.plus(rule.interval())));
            }
        }

        private WindowState activeWindowState(RateLimitWindowRule rule, Instant now) {
            WindowState state = windows.get(rule.key());
            if (state == null || !now.isBefore(state.expiresAt)) {
                return new WindowState(0, now.plus(rule.window()));
            }
            return state;
        }

        private static final class WindowState {

            private int count;
            private final Instant expiresAt;

            private WindowState(int count, Instant expiresAt) {
                this.count = count;
                this.expiresAt = expiresAt;
            }
        }

        private record CooldownState(Instant nextAllowedAt) {
        }

        private List<String> lastWindowKeys() {
            return lastWindowKeys;
        }
    }
}
