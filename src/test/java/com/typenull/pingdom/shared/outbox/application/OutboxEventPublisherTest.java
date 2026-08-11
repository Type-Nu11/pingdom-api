package com.typenull.pingdom.shared.outbox.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.shared.outbox.domain.OutboxEvent;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventStatus;
import com.typenull.pingdom.shared.outbox.domain.OutboxEventType;
import com.typenull.pingdom.shared.outbox.infrastructure.OutboxEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OutboxEventPublisherTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    private OutboxEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new OutboxEventPublisher(
                outboxEventRepository,
                new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-08-11T00:00:00Z"), ZoneOffset.UTC)
        );
    }

    @Test
    void publishCoalescedSkipsNewEventWhenSameAggregateIsWaiting() {
        when(outboxEventRepository.existsByEventTypeAndAggregateTypeAndAggregateIdAndStatusIn(
                any(),
                any(),
                any(),
                anyCollection()
        )).thenReturn(true);

        String eventId = publisher.publishCoalesced(
                "dedup-2",
                OutboxEventType.PLACE_RECOMMENDATION_RESYNC_REQUESTED,
                java.util.Map.of("placeId", 17L),
                "MAP_PLACE",
                "17"
        );

        assertThat(eventId).isNull();
        verify(outboxEventRepository).existsByEventTypeAndAggregateTypeAndAggregateIdAndStatusIn(
                eq(OutboxEventType.PLACE_RECOMMENDATION_RESYNC_REQUESTED),
                eq("MAP_PLACE"),
                eq("17"),
                eq(List.of(OutboxEventStatus.PENDING, OutboxEventStatus.RETRY))
        );
        verify(outboxEventRepository, never()).save(any(OutboxEvent.class));
    }

    @Test
    void publishCoalescedCreatesFollowUpWhenNoWaitingEventExists() {
        when(outboxEventRepository.existsByEventTypeAndAggregateTypeAndAggregateIdAndStatusIn(
                any(),
                any(),
                any(),
                anyCollection()
        )).thenReturn(false);
        when(outboxEventRepository.existsByDeduplicationKey("dedup-2")).thenReturn(false);
        when(outboxEventRepository.save(any(OutboxEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        String eventId = publisher.publishCoalesced(
                "dedup-2",
                OutboxEventType.PLACE_RECOMMENDATION_RESYNC_REQUESTED,
                java.util.Map.of("placeId", 17L),
                "MAP_PLACE",
                "17"
        );

        assertThat(eventId).isNotBlank();
        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }
}
