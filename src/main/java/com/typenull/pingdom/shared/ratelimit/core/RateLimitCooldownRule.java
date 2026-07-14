package com.typenull.pingdom.shared.ratelimit.core;

import java.time.Duration;

public record RateLimitCooldownRule(
        String key,
        Duration interval
) {
}
