package com.typenull.pingdom.shared.ratelimit;

import java.time.Duration;

public record RateLimitWindowRule(
        String key,
        int limit,
        Duration window
) {
}
