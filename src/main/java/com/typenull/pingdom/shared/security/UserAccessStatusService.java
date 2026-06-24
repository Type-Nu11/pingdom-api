package com.typenull.pingdom.shared.security;

import com.typenull.pingdom.identity.domain.User;
import com.typenull.pingdom.identity.domain.UserBanType;
import com.typenull.pingdom.identity.domain.UserStatus;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserAccessStatusService {

    private static final Duration CACHE_TTL = Duration.ofSeconds(10);

    private final UserRepository userRepository;
    private final Clock clock;
    private final Map<Long, CacheEntry> cache = new ConcurrentHashMap<>();

    public boolean canAuthenticate(Long userId) {
        if (userId == null) {
            return false;
        }

        Instant now = Instant.now(clock);
        CacheEntry cached = cache.get(userId);
        if (cached != null && now.isBefore(cached.expiresAt())) {
            return cached.allowed();
        }

        LocalDateTime localNow = LocalDateTime.now(clock);
        User user = userRepository.findById(userId).orElse(null);
        boolean allowed = user != null
                && user.getStatus() == UserStatus.ACTIVE
                && !user.isCurrentlyBanned(localNow);
        cache.put(userId, new CacheEntry(allowed, resolveCacheExpiresAt(user, now, localNow)));
        return allowed;
    }

    public boolean canAuthenticateForAppeal(Long userId) {
        if (userId == null) {
            return false;
        }

        User user = userRepository.findById(userId).orElse(null);
        return user != null && user.getStatus() == UserStatus.ACTIVE;
    }

    public void evict(Long userId) {
        if (userId != null) {
            cache.remove(userId);
        }
    }

    @Scheduled(
            fixedDelayString = "${user.access-status.cache-cleanup-delay:PT1M}",
            initialDelayString = "${user.access-status.cache-cleanup-initial-delay:PT1M}"
    )
    public void cleanExpiredCache() {
        Instant now = Instant.now(clock);
        cache.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expiresAt()));
    }

    private record CacheEntry(boolean allowed, Instant expiresAt) {
    }

    private Instant resolveCacheExpiresAt(User user, Instant now, LocalDateTime localNow) {
        if (user == null
                || !user.isCurrentlyBanned(localNow)
                || user.getBanType() != UserBanType.TEMPORARY
                || user.getBanExpiresAt() == null) {
            return now.plus(CACHE_TTL);
        }

        Duration untilBanExpires = Duration.between(localNow, user.getBanExpiresAt());
        if (untilBanExpires.isNegative() || untilBanExpires.isZero()) {
            return now;
        }

        return now.plus(CACHE_TTL.compareTo(untilBanExpires) <= 0 ? CACHE_TTL : untilBanExpires);
    }
}
