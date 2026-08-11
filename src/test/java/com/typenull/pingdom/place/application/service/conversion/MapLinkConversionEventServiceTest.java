package com.typenull.pingdom.place.application.service.conversion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import com.typenull.pingdom.place.domain.conversion.*;
import com.typenull.pingdom.place.infrastructure.persistence.conversion.MapLinkConversionEventRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class MapLinkConversionEventServiceTest {
    @Test
    void recordsEachRequestIdOnlyOnce() {
        var repository = mock(MapLinkConversionEventRepository.class);
        var writer = mock(MapLinkConversionEventWriter.class);
        var service = new MapLinkConversionEventService(repository, writer);
        when(repository.findByDeduplicationKey(anyString())).thenReturn(Optional.empty());
        when(writer.insert(any(MapLinkConversionEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, MapLinkConversionEvent.class));

        var event = service.record(1L, 2L, MapLinkConversionType.DIRECTIONS, "KAKAO", "req-1", LocalDateTime.now());

        assertThat(event.getProvider()).isEqualTo("KAKAO");
        verify(writer).insert(any(MapLinkConversionEvent.class));
    }

    @Test
    void returnsExistingEventWithoutWritingForSequentialRetry() {
        var repository = mock(MapLinkConversionEventRepository.class);
        var writer = mock(MapLinkConversionEventWriter.class);
        var service = new MapLinkConversionEventService(repository, writer);
        var existing = MapLinkConversionEvent.create(
                1L,
                2L,
                MapLinkConversionType.DIRECTIONS,
                "KAKAO",
                "MAP_LINK:DIRECTIONS:1:2:req-1",
                LocalDateTime.now()
        );
        when(repository.findByDeduplicationKey(anyString())).thenReturn(Optional.of(existing));

        var event = service.record(1L, 2L, MapLinkConversionType.DIRECTIONS, "KAKAO", "req-1", LocalDateTime.now());

        assertThat(event).isSameAs(existing);
        verifyNoInteractions(writer);
    }

    @Test
    void returnsWinningEventWhenConcurrentInsertHitsUniqueConstraint() {
        var repository = mock(MapLinkConversionEventRepository.class);
        var writer = mock(MapLinkConversionEventWriter.class);
        var service = new MapLinkConversionEventService(repository, writer);
        var existing = MapLinkConversionEvent.create(
                1L,
                2L,
                MapLinkConversionType.DIRECTIONS,
                "KAKAO",
                "MAP_LINK:DIRECTIONS:1:2:req-1",
                LocalDateTime.now()
        );
        when(repository.findByDeduplicationKey(anyString()))
                .thenReturn(Optional.empty(), Optional.of(existing));
        when(writer.insert(any(MapLinkConversionEvent.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        var event = service.record(1L, 2L, MapLinkConversionType.DIRECTIONS, "KAKAO", "req-1", LocalDateTime.now());

        assertThat(event).isSameAs(existing);
        verify(writer).insert(any(MapLinkConversionEvent.class));
    }
}
