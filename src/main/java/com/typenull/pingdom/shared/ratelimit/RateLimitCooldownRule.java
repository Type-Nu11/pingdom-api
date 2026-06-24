package com.typenull.pingdom.shared.ratelimit;

import java.time.Duration;

public record RateLimitCooldownRule(
        String key,
        Duration interval
) {
}
