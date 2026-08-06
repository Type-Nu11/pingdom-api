package com.typenull.pingdom.place.application.service.conversion;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.place.domain.conversion.PlaceConversionEvent;
import com.typenull.pingdom.place.domain.conversion.PlaceConversionEventType;
import com.typenull.pingdom.place.infrastructure.persistence.conversion.PlaceConversionEventRepository;
import com.typenull.pingdom.shared.outbox.application.OutboxEventPublisher;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PlaceConversionEventServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 5, 12, 0);

    private final PlaceConversionEventRepository repository = org.mockito.Mockito.mock(
            PlaceConversionEventRepository.class
    );
    private final OutboxEventPublisher outboxEventPublisher = org.mockito.Mockito.mock(OutboxEventPublisher.class);
    private final PlaceConversionEventService service = new PlaceConversionEventService(
            repository,
            outboxEventPublisher
    );

    @Test
    void recordsConversionAndPublishesOutboxEventOnce() {
        when(repository.findByDeduplicationKey("PLACE_CONVERSION_EVENT:RESERVATION:101"))
                .thenReturn(Optional.empty());
        when(repository.save(any(PlaceConversionEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.publish(7L, 11L, PlaceConversionEventType.RESERVATION, 101L, NOW);

        verify(repository).save(any(PlaceConversionEvent.class));
        verify(outboxEventPublisher).publish(
                eq("PLACE_CONVERSION_EVENT:RESERVATION:101"),
                eq(OutboxEventType.PLACE_CONVERSION_RECORDED),
                any(),
                eq("PLACE_CONVERSION_EVENT"),
                any()
        );
    }

    @Test
    void ignoresPreviouslyRecordedSource() {
        PlaceConversionEvent existing = PlaceConversionEvent.create(
                7L, 11L, PlaceConversionEventType.BENEFIT, 202L,
                "PLACE_CONVERSION_EVENT:BENEFIT:202", NOW, NOW
        );
        when(repository.findByDeduplicationKey("PLACE_CONVERSION_EVENT:BENEFIT:202"))
                .thenReturn(Optional.of(existing));

        service.publish(7L, 11L, PlaceConversionEventType.BENEFIT, 202L, NOW);

        verify(repository, never()).save(any());
        verify(outboxEventPublisher, never()).publish(
                any(), any(), any(), any(), any()
        );
    }
}
