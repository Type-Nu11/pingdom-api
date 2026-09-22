package com.typenull.pingdom.place.application.service.conversion;

import com.typenull.pingdom.place.domain.conversion.MapLinkConversionEvent;
import com.typenull.pingdom.place.domain.conversion.MapLinkConversionType;
import com.typenull.pingdom.place.infrastructure.persistence.conversion.MapLinkConversionEventRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 지도 외부 링크 전환을 유형·회원·장소·요청 ID로 중복 제거.
 * 별도 트랜잭션의 insert가 고유 제약으로 실패하면 기존 이벤트를 다시 읽고 없으면 원래 예외를 전달.
 */
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
