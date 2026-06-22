package com.typenull.pingdom.shared.outbox.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.shared.outbox.domain.OutboxEventStatus;
import com.typenull.pingdom.shared.outbox.infrastructure.OutboxEventRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class OutboxEventCleanupServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-22T03:00:00Z");

    @Mock
    private OutboxEventRepository outboxEventRepository;

    private OutboxEventCleanupService cleanupService;

    @BeforeEach
    void setUp() {
        OutboxProperties properties = new OutboxProperties(
                20,
                2,
                20,
                100,
                5,
                Duration.ofSeconds(10),
                Duration.ofMinutes(10),
                Duration.ofMinutes(5),
                Duration.ofDays(7)
        );
        cleanupService = new OutboxEventCleanupService(
                outboxEventRepository,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void cleanupDeletesOnlyConfiguredBatch() {
        LocalDateTime threshold = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC).minusDays(7);
        when(outboxEventRepository.findProcessedEventIdsBefore(
                eq(OutboxEventStatus.SUCCEEDED),
                eq(threshold),
                org.mockito.ArgumentMatchers.any(Pageable.class)
        )).thenReturn(List.of("event-1", "event-2"));

        int deletedCount = cleanupService.cleanupSucceededEvents();

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(outboxEventRepository).findProcessedEventIdsBefore(
                eq(OutboxEventStatus.SUCCEEDED),
                eq(threshold),
                pageableCaptor.capture()
        );
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(100);
        verify(outboxEventRepository).deleteAllByIdInBatch(List.of("event-1", "event-2"));
        assertThat(deletedCount).isEqualTo(2);
    }
}
