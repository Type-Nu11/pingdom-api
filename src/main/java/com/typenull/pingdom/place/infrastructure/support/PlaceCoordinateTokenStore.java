package com.typenull.pingdom.place.infrastructure.support;

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

    public String put(long userId, String kakaoPlaceId, double latitude, double longitude) {
        String token = UUID.randomUUID().toString();
        store.put(token, new Entry(userId, kakaoPlaceId, latitude, longitude, Instant.now(clock).plus(TTL)));
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

    @Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT1M")
    public void evictExpiredTokens() {
        Instant now = Instant.now(clock);
        store.entrySet().removeIf(entry -> now.isAfter(entry.getValue().expiresAt()));
    }

    public record Entry(long userId, String kakaoPlaceId, double latitude, double longitude, Instant expiresAt) {
    }
}
