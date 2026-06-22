package com.typenull.pingdom.shared.security;

import com.typenull.pingdom.identity.domain.UserStatus;
import com.typenull.pingdom.identity.domain.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserAccessStatusService {

    private static final Duration CACHE_TTL = Duration.ofSeconds(10);

    private final UserRepository userRepository;
    private final Map<Long, CacheEntry> cache = new ConcurrentHashMap<>();

    public boolean canAuthenticate(Long userId) {
        if (userId == null) {
            return false;
        }

        Instant now = Instant.now();
        CacheEntry cached = cache.get(userId);
        if (cached != null && now.isBefore(cached.expiresAt())) {
            return cached.allowed();
        }

        boolean allowed = userRepository.existsByIdAndStatusAndBannedFalse(userId, UserStatus.ACTIVE);
        cache.put(userId, new CacheEntry(allowed, now.plus(CACHE_TTL)));
        return allowed;
    }

    public void evict(Long userId) {
        if (userId != null) {
            cache.remove(userId);
        }
    }

    private record CacheEntry(boolean allowed, Instant expiresAt) {
    }
}
