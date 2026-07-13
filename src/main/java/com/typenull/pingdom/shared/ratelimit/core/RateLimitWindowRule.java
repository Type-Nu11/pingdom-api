package com.typenull.pingdom.shared.ratelimit.core;

import java.time.Duration;

public record RateLimitWindowRule(
        String key,
        int limit,
        Duration window
) {
}
