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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AbuseRateLimitServiceTest {

    private MutableClock clock;
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
                1_000
        );
        InMemoryRateLimitStore store = new InMemoryRateLimitStore(properties, clock);
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
}
