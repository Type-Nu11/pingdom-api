package com.typenull.pingdom.shared.ratelimit.store;

import com.typenull.pingdom.shared.ratelimit.core.RateLimitCooldownRule;
import com.typenull.pingdom.shared.ratelimit.core.RateLimitWindowRule;

import java.util.Collection;

/** 행위에 속한 요청 수·대기 간격 규칙의 허용 여부를 판정하고 허용된 요청을 소비. */
public interface RateLimitStore {

    void acquire(
            String message,
            Collection<RateLimitWindowRule> windowRules,
            Collection<RateLimitCooldownRule> cooldownRules
    );
}
