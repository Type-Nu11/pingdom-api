package com.typenull.pingdom.place.application.service.conversion;

import com.typenull.pingdom.place.domain.conversion.MapLinkConversionEvent;
import com.typenull.pingdom.place.domain.conversion.MapLinkConversionType;
import com.typenull.pingdom.place.infrastructure.persistence.conversion.MapLinkConversionEventRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MapLinkConversionEventService {
    private final MapLinkConversionEventRepository repository;
    private final MapLinkConversionEventWriter writer;

    public MapLinkConversionEvent record(long userId, long placeId, MapLinkConversionType type,
                                         String provider, String requestId, LocalDateTime occurredAt) {
        String key = "MAP_LINK:%s:%d:%d:%s".formatted(type, userId, placeId, requestId.trim());
        return repository.findByDeduplicationKey(key)
                .orElseGet(() -> insertOrLoadExisting(userId, placeId, type, provider, key, occurredAt));
    }

    private MapLinkConversionEvent insertOrLoadExisting(
            long userId,
            long placeId,
            MapLinkConversionType type,
            String provider,
            String key,
            LocalDateTime occurredAt
    ) {
        try {
            return writer.insert(MapLinkConversionEvent.create(userId, placeId, type, provider, key, occurredAt));
        } catch (DataIntegrityViolationException exception) {
            return repository.findByDeduplicationKey(key).orElseThrow(() -> exception);
        }
    }
}
