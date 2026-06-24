package com.typenull.pingdom.shared.ratelimit;

import java.util.Collection;

public interface RateLimitStore {

    void acquire(
            String message,
            Collection<RateLimitWindowRule> windowRules,
            Collection<RateLimitCooldownRule> cooldownRules
    );
}
