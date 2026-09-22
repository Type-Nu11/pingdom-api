package com.typenull.pingdom.shared.ratelimit.store;

import com.typenull.pingdom.shared.ratelimit.config.AbuseRateLimitProperties.EmailResendPolicy;
import com.typenull.pingdom.shared.ratelimit.config.AbuseRateLimitProperties.StorageType;
import com.typenull.pingdom.shared.ratelimit.config.AbuseRateLimitProperties.WindowPolicy;

import com.typenull.pingdom.shared.ratelimit.config.AbuseRateLimitProperties;
import com.typenull.pingdom.shared.ratelimit.core.RateLimitWindowRule;
import com.typenull.pingdom.shared.ratelimit.exception.RateLimitException;
import com.typenull.pingdom.shared.ratelimit.exception.RateLimitUnavailableException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

    /**
     * Redis 대역과 fail-open=true 설정을 연결해 Lua 결과·장애 처리 분기를 검증.
     */
    @BeforeEach
    void setUp() {
        redisRateLimitStore = new RedisRateLimitStore(redisTemplate, properties(true));
    }

    /**
     * Redis script가 0을 반환하면 RateLimitException으로 요청을 거절하는지 검증.
     */
    @Test
    void rejectsDeniedRedisRequest() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(0L);

        assertThrows(RateLimitException.class, () -> redisRateLimitStore.acquire(
                "too many requests",
                List.of(new RateLimitWindowRule("login:user", 1, Duration.ofMinutes(1))),
                List.of()
        ));
    }

    /**
     * Redis script가 1을 반환하면 예외 없이 요청을 허용하는지 검증.
     */
    @Test
    void allowsAcceptedRedisRequest() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(1L);

        assertDoesNotThrow(() -> redisRateLimitStore.acquire(
                "too many requests",
                List.of(new RateLimitWindowRule("login:user", 1, Duration.ofMinutes(1))),
                List.of()
        ));
    }

    /**
     * fail-open=false에서 Redis 결과가 null이면 RateLimitUnavailableException으로 차단하는지 검증.
     */
    @Test
    void failsClosedOnUnexpectedResult() {
        RedisRateLimitStore failClosedStore = new RedisRateLimitStore(redisTemplate, properties(false));
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(null);

        assertThrows(RateLimitUnavailableException.class, () -> failClosedStore.acquire(
                "too many requests",
                List.of(new RateLimitWindowRule("login:user", 1, Duration.ofMinutes(1))),
                List.of()
        ));
    }

    /**
     * 명시적 fail-open 설정에서는 Redis 장애 예외에도 요청을 허용하는지 검증.
     */
    @Test
    void failsOpenOnRedisFailure() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new IllegalStateException("redis unavailable"));

        assertDoesNotThrow(() -> redisRateLimitStore.acquire(
                "too many requests",
                List.of(new RateLimitWindowRule("login:user", 1, Duration.ofMinutes(1))),
                List.of()
        ));
    }

    /**
     * fail-open=false에서 Redis 장애는 RATE_LIMIT_UNAVAILABLE 코드의 예외로 변환되는지 검증.
     */
    @Test
    void failsClosedOnRedisFailure() {
        RedisRateLimitStore failClosedStore = new RedisRateLimitStore(redisTemplate, properties(false));
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new IllegalStateException("redis unavailable"));

        RateLimitUnavailableException exception = assertThrows(RateLimitUnavailableException.class, () -> failClosedStore.acquire(
                "too many requests",
                List.of(new RateLimitWindowRule("login:user", 1, Duration.ofMinutes(1))),
                List.of()
        ));

        assertEquals("RATE_LIMIT_UNAVAILABLE", exception.getCode());
    }

    /**
     * fail-open 값을 생략하면 Redis 장애 시 기본적으로 요청을 차단하는지 검증.
     */
    @Test
    void defaultsToFailClosed() {
        RedisRateLimitStore defaultStore = new RedisRateLimitStore(redisTemplate, properties(null));
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new IllegalStateException("redis unavailable"));

        assertThrows(RateLimitUnavailableException.class, () -> defaultStore.acquire(
                "too many requests",
                List.of(new RateLimitWindowRule("login:user", 1, Duration.ofMinutes(1))),
                List.of()
        ));
    }

    /**
     * login 사용자명·IP 키가 같은 {login} hash tag와 설정 접두사를 사용해 Lua 키가 같은 Redis Cluster 슬롯에 배치되도록 하는지 검증.
     */
    @Test
    void groupsRedisClusterHashTags() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(1L);

        redisRateLimitStore.acquire(
                "too many requests",
                List.of(
                        new RateLimitWindowRule("login:username:abc", 1, Duration.ofMinutes(1)),
                        new RateLimitWindowRule("login:ip:198.51.100.10", 100, Duration.ofMinutes(1))
                ),
                List.of()
        );

        ArgumentCaptor<List> keysCaptor = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate).execute(any(DefaultRedisScript.class), keysCaptor.capture(), any(Object[].class));

        assertEquals(
                List.of(
                        "test:rate-limit:{login}:login:username:abc",
                        "test:rate-limit:{login}:login:ip:198.51.100.10"
                ),
                keysCaptor.getValue()
        );
    }

    /**
     * 창·쿨다운·키 접두사는 고정하고 failOpen만 가변으로 하여 저장소 장애 정책의 비교 입력을 생성.
     */
    private AbuseRateLimitProperties properties(Boolean failOpen) {
        return new AbuseRateLimitProperties(
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
                failOpen
        );
    }
}
