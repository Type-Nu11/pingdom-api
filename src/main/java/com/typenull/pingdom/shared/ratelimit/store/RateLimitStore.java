package com.typenull.pingdom.shared.ratelimit.store;

import com.typenull.pingdom.shared.ratelimit.core.RateLimitCooldownRule;
import com.typenull.pingdom.shared.ratelimit.core.RateLimitWindowRule;

import java.util.Collection;

public interface RateLimitStore {

    void acquire(
            String message,
            Collection<RateLimitWindowRule> windowRules,
            Collection<RateLimitCooldownRule> cooldownRules
    );
}
