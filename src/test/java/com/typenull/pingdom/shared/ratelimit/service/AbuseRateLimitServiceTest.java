package com.typenull.pingdom.shared.ratelimit.service;

import com.typenull.pingdom.shared.ratelimit.config.AbuseRateLimitProperties.EmailResendPolicy;
import com.typenull.pingdom.shared.ratelimit.config.AbuseRateLimitProperties.StorageType;
import com.typenull.pingdom.shared.ratelimit.config.AbuseRateLimitProperties.WindowPolicy;

import com.typenull.pingdom.shared.ratelimit.config.AbuseRateLimitProperties;
import com.typenull.pingdom.shared.ratelimit.core.RateLimitCooldownRule;
import com.typenull.pingdom.shared.ratelimit.core.RateLimitWindowRule;
import com.typenull.pingdom.shared.ratelimit.exception.RateLimitException;
import com.typenull.pingdom.shared.ratelimit.store.RateLimitStore;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    /**
     * 고정 Clock과 메모리 저장소에 사용자별 2회 제한·재전송 1분 간격 등을 설정해 정책 키와 시간 경계를 검증.
     */
    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-06-23T00:00:00Z"), ZoneOffset.UTC);
        AbuseRateLimitProperties properties = new AbuseRateLimitProperties(
                StorageType.REDIS,
                new WindowPolicy(2, Duration.ofHours(1)),
                new WindowPolicy(100, Duration.ofHours(1)),
                new WindowPolicy(2, Duration.ofMinutes(1)),
                new WindowPolicy(100, Duration.ofMinutes(1)),
                new WindowPolicy(2, Duration.ofMinutes(1)),
                new WindowPolicy(100, Duration.ofMinutes(1)),
                new EmailResendPolicy(
                        Duration.ofMinutes(1),
                        new WindowPolicy(5, Duration.ofDays(1)),
                        new WindowPolicy(100, Duration.ofDays(1))
                ),
                new WindowPolicy(2, Duration.ofMinutes(10)),
                new WindowPolicy(100, Duration.ofMinutes(10)),
                new EmailResendPolicy(
                        Duration.ofMinutes(1),
                        new WindowPolicy(5, Duration.ofDays(1)),
                        new WindowPolicy(100, Duration.ofDays(1))
                ),
                new WindowPolicy(2, Duration.ofMinutes(1)),
                new WindowPolicy(100, Duration.ofMinutes(1)),
                new WindowPolicy(2, Duration.ofHours(1)),
                new WindowPolicy(100, Duration.ofHours(1)),
                new WindowPolicy(2, Duration.ofMinutes(1)),
                new WindowPolicy(100, Duration.ofMinutes(1)),
                new WindowPolicy(2, Duration.ofMinutes(1)),
                new WindowPolicy(100, Duration.ofMinutes(1)),
                new WindowPolicy(1, Duration.ofHours(1)),
                new WindowPolicy(100, Duration.ofHours(1)),
                new WindowPolicy(2, Duration.ofMinutes(1)),
                "test:rate-limit:",
                true
        );
        store = new FakeRateLimitStore(clock);
        abuseRateLimitService = new AbuseRateLimitService(properties, store);
    }

    /**
     * 대소문자·공백만 다른 이메일은 IP가 달라도 같은 가입 횟수 창을 공유하여 세 번째 요청이 거절되는지 검증.
     */
    @Test
    void limitsNormalizedSignupEmail() {
        abuseRateLimitService.checkSignup("User@Example.com", "203.0.113.14");
        abuseRateLimitService.checkSignup("user@example.com", "203.0.113.15");

        assertThrows(RateLimitException.class, () ->
                abuseRateLimitService.checkSignup(" USER@example.com ", "203.0.113.16")
        );
    }

    /**
     * 사용자명 대소문자를 정규화해 세 번째 로그인을 거절하고 1분 경계 뒤 다시 허용하는지 검증.
     */
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

    /**
     * 같은 IP의 세 번째 상담 도입 요청은 거절하고 다른 IP는 허용해 익명 요청 제한 범위를 검증.
     */
    @Test
    void limitsConsultationIntroPerIp() {
        abuseRateLimitService.checkConsultationIntro("203.0.113.90");
        abuseRateLimitService.checkConsultationIntro("203.0.113.90");

        assertThrows(RateLimitException.class, () ->
                abuseRateLimitService.checkConsultationIntro("203.0.113.90")
        );
        assertDoesNotThrow(() -> abuseRateLimitService.checkConsultationIntro("203.0.113.91"));
    }

    /**
     * 대소문자가 다른 같은 이메일도 재전송 쿨다운을 공유하며 1분 경계 뒤 재전송이 허용되는지 검증.
     */
    @Test
    void enforcesEmailResendCooldown() {
        abuseRateLimitService.checkEmailResend("User@Example.com", "203.0.113.20");

        assertThrows(RateLimitException.class, () ->
                abuseRateLimitService.checkEmailResend("user@example.com", "203.0.113.21")
        );

        clock.advance(Duration.ofMinutes(1));

        assertDoesNotThrow(() -> abuseRateLimitService.checkEmailResend("user@example.com", "203.0.113.22"));
    }

    /**
     * 대소문자·공백만 다른 이메일 인증 요청이 IP와 무관하게 같은 횟수 제한을 공유하는지 검증.
     */
    @Test
    void limitsNormalizedEmailVerification() {
        abuseRateLimitService.checkEmailVerify("User@Example.com", "203.0.113.23");
        abuseRateLimitService.checkEmailVerify("user@example.com", "203.0.113.24");

        assertThrows(RateLimitException.class, () ->
                abuseRateLimitService.checkEmailVerify(" USER@example.com ", "203.0.113.25")
        );
    }

    /**
     * 비밀번호 초기화 요청이 이메일 정규화 후 1분 재요청 간격을 적용하고 경계 시각에는 허용되는지 검증.
     */
    @Test
    void enforcesPasswordResetCooldown() {
        abuseRateLimitService.checkPasswordResetRequest("User@Example.com", "203.0.113.20");

        assertThrows(RateLimitException.class, () ->
                abuseRateLimitService.checkPasswordResetRequest("user@example.com", "203.0.113.21")
        );

        clock.advance(Duration.ofMinutes(1));

        assertDoesNotThrow(() ->
                abuseRateLimitService.checkPasswordResetRequest("user@example.com", "203.0.113.22")
        );
    }

    /**
     * 대소문자만 다른 초기화 토큰은 서로 다른 제한 키를 만들어 토큰 식별자를 소문자로 합치지 않는지 검증.
     */
    @Test
    void preservesResetTokenFingerprintCase() {
        abuseRateLimitService.checkPasswordResetConfirm("Reset.Token.A", "203.0.113.50");
        String upperTokenKey = store.lastWindowKeys().stream()
                .filter(key -> key.startsWith("password-reset-confirm:token:"))
                .findFirst()
                .orElseThrow();

        abuseRateLimitService.checkPasswordResetConfirm("Reset.Token.a", "203.0.113.51");
        String lowerTokenKey = store.lastWindowKeys().stream()
                .filter(key -> key.startsWith("password-reset-confirm:token:"))
                .findFirst()
                .orElseThrow();

        org.junit.jupiter.api.Assertions.assertNotEquals(upperTokenKey, lowerTokenKey);
    }

    /**
     * 사용자 1의 두 번째 업로드는 다른 IP라도 거절하고 사용자 2는 허용해 사용자별 창을 검증.
     */
    @Test
    void imageUploadLimitUsesUserWindow() {
        abuseRateLimitService.checkImageUpload(1L, "203.0.113.30");

        assertThrows(RateLimitException.class, () ->
                abuseRateLimitService.checkImageUpload(1L, "203.0.113.31")
        );

        assertDoesNotThrow(() -> abuseRateLimitService.checkImageUpload(2L, "203.0.113.32"));
    }

    /**
     * 사용자 1의 세 번째 추천 클릭은 IP가 바뀌어도 거절하고 다른 사용자는 허용하는지 검증.
     */
    @Test
    void limitsRecommendationClicksPerUser() {
        abuseRateLimitService.checkRecommendationClick(1L, "203.0.113.60");
        abuseRateLimitService.checkRecommendationClick(1L, "203.0.113.61");

        assertThrows(RateLimitException.class, () ->
                abuseRateLimitService.checkRecommendationClick(1L, "203.0.113.62")
        );

        assertDoesNotThrow(() -> abuseRateLimitService.checkRecommendationClick(2L, "203.0.113.63"));
    }

    /**
     * 대소문자만 다른 리프레시 토큰은 서로 다른 제한 키를 사용해 토큰 식별을 보존하는지 검증.
     */
    @Test
    void preservesRefreshTokenFingerprintCase() {
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

        /**
         * 제한 창과 쿨다운 경계를 재현할 초기 Instant·시간대를 보관.
         */
        private MutableClock(Instant instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        /**
         * 테스트 Clock의 지정 시간대를 반환.
         */
        @Override
        public ZoneId getZone() {
            return zone;
        }

        /**
         * 동일 현재 시각에 새 시간대를 가진 독립 Clock을 제공.
         */
        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(instant, zone);
        }

        /**
         * 테스트가 전진시킨 현재 Instant를 제한 저장소에 제공.
         */
        @Override
        public Instant instant() {
            return instant;
        }

        /**
         * 실제 대기 없이 시간을 전진시켜 고정 창 만료와 쿨다운 해제를 재현.
         */
        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }
    }

    private static class FakeRateLimitStore implements RateLimitStore {

        private final Clock clock;
        private final Map<String, WindowState> windows = new ConcurrentHashMap<>();
        private final Map<String, CooldownState> cooldowns = new ConcurrentHashMap<>();
        private List<String> lastWindowKeys = List.of();

        /**
         * 시간 제어용 Clock을 저장해 메모리 창·쿨다운의 만료를 판정.
         */
        private FakeRateLimitStore(Clock clock) {
            this.clock = clock;
        }

        /**
         * 쿨다운과 모든 창의 한도를 먼저 확인한 뒤 허용 요청만 카운터·다음 허용 시각에 반영.
         * 정책 단위 테스트의 순차 실행용 대역. Redis 원자성과 실제 동시 경합은 검증 범위에서 제외.
         */
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

        /**
         * 창이 없거나 만료 경계에 도달하면 새 0회 창을 만들고 아직 유효한 창은 유지.
         */
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

            /**
             * 메모리 제한 창의 현재 요청 횟수와 만료 시각을 보관.
             */
            private WindowState(int count, Instant expiresAt) {
                this.count = count;
                this.expiresAt = expiresAt;
            }
        }

        private record CooldownState(Instant nextAllowedAt) {
        }

        /**
         * 마지막 acquire에 전달된 창 키를 노출해 토큰 fingerprint의 대소문자 보존을 비교.
         */
        private List<String> lastWindowKeys() {
            return lastWindowKeys;
        }
    }
}
