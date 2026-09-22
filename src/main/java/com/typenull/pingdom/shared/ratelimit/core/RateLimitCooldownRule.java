package com.typenull.pingdom.shared.ratelimit.core;

import java.time.Duration;

/** 같은 key의 재요청을 interval 동안 막는 규칙. Redis 저장소는 Duration을 밀리초 TTL로 변환. */
public record RateLimitCooldownRule(
        String key,
        Duration interval
) {
}
