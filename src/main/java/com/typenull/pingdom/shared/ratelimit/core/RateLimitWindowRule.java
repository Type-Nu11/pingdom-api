package com.typenull.pingdom.shared.ratelimit.core;

import java.time.Duration;

/** key별 window 동안 허용할 요청 수 limit을 전달한다. 구간은 저장소의 첫 요청 시점부터 시작한다. */
public record RateLimitWindowRule(
        String key,
        int limit,
        Duration window
) {
}
