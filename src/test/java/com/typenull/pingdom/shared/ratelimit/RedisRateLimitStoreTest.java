package com.typenull.pingdom.shared.ratelimit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.shared.ratelimit.AbuseRateLimitProperties.EmailResendPolicy;
import com.typenull.pingdom.shared.ratelimit.AbuseRateLimitProperties.WindowPolicy;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "rawtypes"})
class RedisRateLimitStoreTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    private RedisRateLimitStore redisRateLimitStore;

    @BeforeEach
    void setUp() {
        redisRateLimitStore = new RedisRateLimitStore(redisTemplate, properties(true));
    }

    @Test
    void acquireThrowsRateLimitExceptionWhenRedisScriptDeniesRequest() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(0L);

        assertThrows(RateLimitException.class, () -> redisRateLimitStore.acquire(
                "too many requests",
                List.of(new RateLimitWindowRule("login:user", 1, Duration.ofMinutes(1))),
                List.of()
        ));
    }

    @Test
    void acquireAllowsRequestWhenRedisScriptAllowsRequest() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(1L);

        assertDoesNotThrow(() -> redisRateLimitStore.acquire(
                "too many requests",
                List.of(new RateLimitWindowRule("login:user", 1, Duration.ofMinutes(1))),
                List.of()
        ));
    }

    @Test
    void acquireFailsOpenWhenRedisThrowsException() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new IllegalStateException("redis unavailable"));

        assertDoesNotThrow(() -> redisRateLimitStore.acquire(
                "too many requests",
                List.of(new RateLimitWindowRule("login:user", 1, Duration.ofMinutes(1))),
                List.of()
        ));
    }

    @Test
    void acquirePropagatesRedisExceptionWhenFailOpenIsDisabled() {
        RedisRateLimitStore failClosedStore = new RedisRateLimitStore(redisTemplate, properties(false));
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new IllegalStateException("redis unavailable"));

        assertThrows(IllegalStateException.class, () -> failClosedStore.acquire(
                "too many requests",
                List.of(new RateLimitWindowRule("login:user", 1, Duration.ofMinutes(1))),
                List.of()
        ));
    }

    private AbuseRateLimitProperties properties(boolean failOpen) {
        return new AbuseRateLimitProperties(
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
                failOpen
        );
    }
}
