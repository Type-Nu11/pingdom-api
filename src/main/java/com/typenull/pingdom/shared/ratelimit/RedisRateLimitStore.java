package com.typenull.pingdom.shared.ratelimit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class RedisRateLimitStore implements RateLimitStore {

    private static final String ACQUIRE_SCRIPT = """
            local window_count = tonumber(ARGV[1])
            local cooldown_count = tonumber(ARGV[2])

            for i = 1, window_count do
                local limit = tonumber(ARGV[2 + ((i - 1) * 2) + 1])
                local current = redis.call('GET', KEYS[i])
                if current and tonumber(current) >= limit then
                    return 0
                end
            end

            local cooldown_key_start = window_count + 1
            for i = 1, cooldown_count do
                if redis.call('EXISTS', KEYS[cooldown_key_start + i - 1]) == 1 then
                    return 0
                end
            end

            for i = 1, window_count do
                local ttl = tonumber(ARGV[2 + ((i - 1) * 2) + 2])
                local value = redis.call('INCR', KEYS[i])
                if value == 1 or redis.call('PTTL', KEYS[i]) < 0 then
                    redis.call('PEXPIRE', KEYS[i], ttl)
                end
            end

            local cooldown_arg_start = 2 + (window_count * 2)
            for i = 1, cooldown_count do
                local ttl = tonumber(ARGV[cooldown_arg_start + i])
                redis.call('SET', KEYS[cooldown_key_start + i - 1], '1', 'PX', ttl, 'NX')
            end

            return 1
            """;

    private final StringRedisTemplate redisTemplate;
    private final AbuseRateLimitProperties properties;
    private final DefaultRedisScript<Long> acquireScript;

    public RedisRateLimitStore(StringRedisTemplate redisTemplate, AbuseRateLimitProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.acquireScript = new DefaultRedisScript<>(ACQUIRE_SCRIPT, Long.class);
    }

    @Override
    public void acquire(
            String message,
            Collection<RateLimitWindowRule> windowRules,
            Collection<RateLimitCooldownRule> cooldownRules
    ) {
        try {
            Long result = redisTemplate.execute(
                    acquireScript,
                    keys(windowRules, cooldownRules),
                    (Object[]) args(windowRules, cooldownRules)
            );
            if (Long.valueOf(1L).equals(result)) {
                return;
            }
            if (Long.valueOf(0L).equals(result)) {
                throw new RateLimitException(message);
            }
            throw new IllegalStateException("Redis rate limit 스크립트 결과가 올바르지 않습니다.");
        } catch (RateLimitException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            if (properties.failOpen()) {
                log.warn("Redis rate limit 확인에 실패해 요청을 허용합니다.", exception);
                return;
            }
            log.error("Redis rate limit 확인에 실패해 요청을 차단합니다.", exception);
            throw new RateLimitUnavailableException(exception);
        }
    }

    private List<String> keys(
            Collection<RateLimitWindowRule> windowRules,
            Collection<RateLimitCooldownRule> cooldownRules
    ) {
        List<String> keys = new ArrayList<>(windowRules.size() + cooldownRules.size());
        for (RateLimitWindowRule rule : windowRules) {
            keys.add(redisKey(rule.key()));
        }
        for (RateLimitCooldownRule rule : cooldownRules) {
            keys.add(redisKey(rule.key()));
        }
        return keys;
    }

    private String[] args(
            Collection<RateLimitWindowRule> windowRules,
            Collection<RateLimitCooldownRule> cooldownRules
    ) {
        List<String> args = new ArrayList<>(2 + (windowRules.size() * 2) + cooldownRules.size());
        args.add(String.valueOf(windowRules.size()));
        args.add(String.valueOf(cooldownRules.size()));
        for (RateLimitWindowRule rule : windowRules) {
            args.add(String.valueOf(rule.limit()));
            args.add(String.valueOf(rule.window().toMillis()));
        }
        for (RateLimitCooldownRule rule : cooldownRules) {
            args.add(String.valueOf(rule.interval().toMillis()));
        }
        return args.toArray(String[]::new);
    }

    private String redisKey(String key) {
        return properties.redisKeyPrefix() + "{" + rateLimitGroup(key) + "}:" + key;
    }

    private String rateLimitGroup(String key) {
        int delimiterIndex = key.indexOf(':');
        if (delimiterIndex <= 0) {
            return "default";
        }
        return key.substring(0, delimiterIndex);
    }
}
