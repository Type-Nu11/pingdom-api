package com.typenull.pingdom.place.infrastructure.support;

import com.typenull.pingdom.place.domain.place.GeocodingSource;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PlaceCoordinateTokenStore {

    private static final Duration TTL = Duration.ofMinutes(10);

    private final Clock clock = Clock.systemUTC();
    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    public String putUserPin(long userId, String kakaoPlaceId, double latitude, double longitude) {
        return put(userId, kakaoPlaceId, latitude, longitude, GeocodingSource.USER_PIN);
    }

    public String putVerifiedKakao(long userId, String kakaoPlaceId, double latitude, double longitude) {
        if (kakaoPlaceId == null || kakaoPlaceId.isBlank()) {
            throw new IllegalArgumentException("검증된 Kakao 장소 ID는 필수입니다.");
        }
        return put(userId, kakaoPlaceId.trim(), latitude, longitude, GeocodingSource.KAKAO);
    }

    private String put(
            long userId,
            String kakaoPlaceId,
            double latitude,
            double longitude,
            GeocodingSource geocodingSource
    ) {
        String token = UUID.randomUUID().toString();
        store.put(token, new Entry(
                userId,
                kakaoPlaceId,
                latitude,
                longitude,
                geocodingSource,
                Instant.now(clock).plus(TTL)
        ));
        return token;
    }

    public Entry consume(String token) {
        if (token == null) {
            return null;
        }

        Entry entry = store.remove(token);
        if (entry == null) {
            return null;
        }

        if (Instant.now(clock).isAfter(entry.expiresAt())) {
            return null;
        }

        return entry;
    }

    public Entry peek(String token) {
        if (token == null) {
            return null;
        }

        Entry entry = store.get(token);
        if (entry == null) {
            return null;
        }

        if (Instant.now(clock).isAfter(entry.expiresAt())) {
            store.remove(token);
            return null;
        }

        return entry;
    }

    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT1M")
    public void evictExpiredTokens() {
        Instant now = Instant.now(clock);
        store.entrySet().removeIf(entry -> now.isAfter(entry.getValue().expiresAt()));
    }

    public record Entry(
            long userId,
            String kakaoPlaceId,
            double latitude,
            double longitude,
            GeocodingSource geocodingSource,
            Instant expiresAt
    ) {
    }
}
