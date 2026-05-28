package com.typenull.pingdom.domain.map.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class PlaceCoordinateTokenStore {

    private static final Duration TTL = Duration.ofMinutes(10);

    private final Clock clock = Clock.systemUTC();
    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    public String put(long userId, double latitude, double longitude) {
        String token = UUID.randomUUID().toString();
        store.put(token, new Entry(userId, latitude, longitude, Instant.now(clock).plus(TTL)));
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

    public record Entry(long userId, double latitude, double longitude, Instant expiresAt) {
    }
}

