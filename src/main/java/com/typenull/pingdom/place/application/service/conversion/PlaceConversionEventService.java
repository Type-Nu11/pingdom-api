package com.typenull.pingdom.place.application.service.conversion;

import com.typenull.pingdom.place.domain.conversion.PlaceConversionEvent;
import com.typenull.pingdom.place.domain.conversion.PlaceConversionEventType;
import com.typenull.pingdom.place.infrastructure.persistence.conversion.PlaceConversionEventRepository;
import com.typenull.pingdom.place.outbox.PlaceConversionEventOutboxPayload;
import com.typenull.pingdom.shared.outbox.application.OutboxEventPublisher;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlaceConversionEventService {

    private final PlaceConversionEventRepository conversionEventRepository;
    private final OutboxEventPublisher outboxEventPublisher;

    @Transactional
    public void publish(
            Long userId,
            Long placeId,
            PlaceConversionEventType conversionType,
            Long sourceId,
            LocalDateTime occurredAt
    ) {
        String deduplicationKey = "PLACE_CONVERSION_EVENT:%s:%d".formatted(conversionType, sourceId);
        if (conversionEventRepository.findByDeduplicationKey(deduplicationKey).isPresent()) {
            return;
        }

        PlaceConversionEvent event = conversionEventRepository.save(
                PlaceConversionEvent.create(
                        userId,
                        placeId,
                        conversionType,
                        sourceId,
                        deduplicationKey,
                        occurredAt,
                        occurredAt
                )
        );
        outboxEventPublisher.publish(
                deduplicationKey,
                OutboxEventType.PLACE_CONVERSION_RECORDED,
                new PlaceConversionEventOutboxPayload(
                        event.getId(), userId, placeId, conversionType, sourceId, occurredAt
                ),
                "PLACE_CONVERSION_EVENT",
                String.valueOf(event.getId())
        );
    }
}
