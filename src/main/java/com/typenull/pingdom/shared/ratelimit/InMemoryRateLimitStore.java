package com.typenull.pingdom.shared.ratelimit;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InMemoryRateLimitStore {

    private final AbuseRateLimitProperties properties;
    private final Clock clock;
    private final Map<String, WindowState> windows = new ConcurrentHashMap<>();
    private final Map<String, CooldownState> cooldowns = new ConcurrentHashMap<>();
    private final Object monitor = new Object();

    public void acquire(
            String message,
            Collection<RateLimitWindowRule> windowRules,
            Collection<RateLimitCooldownRule> cooldownRules
    ) {
        Instant now = Instant.now(clock);

        synchronized (monitor) {
            for (RateLimitCooldownRule rule : cooldownRules) {
                CooldownState state = cooldowns.get(rule.key());
                if (state != null && now.isBefore(state.nextAllowedAt())) {
                    throw new RateLimitException(message);
                }
            }

            for (RateLimitWindowRule rule : windowRules) {
                WindowState state = activeWindowState(rule, now);
                if (state.count >= rule.limit()) {
                    throw new RateLimitException(message);
                }
            }

            for (RateLimitWindowRule rule : windowRules) {
                WindowState state = activeWindowState(rule, now);
                state.count++;
                windows.put(rule.key(), state);
            }

            for (RateLimitCooldownRule rule : cooldownRules) {
                cooldowns.put(rule.key(), new CooldownState(now.plus(rule.interval())));
            }

            if (windows.size() + cooldowns.size() > properties.maxKeys()) {
                evictExpired(now);
            }
        }
    }

    public void clear() {
        synchronized (monitor) {
            windows.clear();
            cooldowns.clear();
        }
    }

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT5M")
    public void evictExpired() {
        synchronized (monitor) {
            evictExpired(Instant.now(clock));
        }
    }

    private WindowState activeWindowState(RateLimitWindowRule rule, Instant now) {
        WindowState state = windows.get(rule.key());
        if (state == null || !now.isBefore(state.expiresAt)) {
            return new WindowState(0, now.plus(rule.window()));
        }
        return state;
    }

    private void evictExpired(Instant now) {
        windows.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expiresAt));
        cooldowns.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().nextAllowedAt()));
    }

    private static final class WindowState {

        private int count;
        private final Instant expiresAt;

        private WindowState(int count, Instant expiresAt) {
            this.count = count;
            this.expiresAt = expiresAt;
        }
    }

    private record CooldownState(Instant nextAllowedAt) {
    }
}
