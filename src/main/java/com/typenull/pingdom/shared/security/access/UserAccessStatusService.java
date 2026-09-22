package com.typenull.pingdom.shared.security.access;

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

/**
 * 계정 활성·정지 상태를 조회해 JWT 인증 가능 여부를 판단하고 결과를 인스턴스 로컬 캐시에 최대 10초 보관.
 * 변경 직후 반영에는 evict가 필요하며, 다른 서버 인스턴스의 캐시는 무효화 대상에서 제외.
 */
@Service
@RequiredArgsConstructor
public class UserAccessStatusService {

    private static final Duration CACHE_TTL = Duration.ofSeconds(10);

    private final UserRepository userRepository;
    private final Clock clock;
    private final Map<Long, CacheEntry> cache = new ConcurrentHashMap<>();

    /** 활성 계정 중 현재 정지되지 않은 사용자만 허용. 임시 정지의 거절 캐시는 정지 종료 시각까지로 제한. */
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

    /** 이의신청 경로는 캐시를 사용하지 않고 ACTIVE 여부만 확인해 정지 중 사용자도 허용. */
    public boolean canAuthenticateForAppeal(Long userId) {
        if (userId == null) {
            return false;
        }

        User user = userRepository.findById(userId).orElse(null);
        return user != null && user.getStatus() == UserStatus.ACTIVE;
    }

    /** 사용자 상태 변경 후 이 인스턴스의 판정 결과를 제거. null 식별자는 무시. */
    public void evict(Long userId) {
        if (userId != null) {
            cache.remove(userId);
        }
    }

    /** 만료 시각에 도달한 로컬 항목을 제거. 인증 조회 자체도 만료 여부를 검사하므로 허용 기간은 청소 주기와 무관. */
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

    /** 임시 정지 종료까지 남은 시간과 10초 중 짧은 값을 선택해 정지 종료 후 불필요한 거절을 줄임. */
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
