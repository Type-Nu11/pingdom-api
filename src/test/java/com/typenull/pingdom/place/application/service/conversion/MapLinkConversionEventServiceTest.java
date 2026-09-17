package com.typenull.pingdom.place.application.service.conversion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import com.typenull.pingdom.place.domain.conversion.*;
import com.typenull.pingdom.place.infrastructure.persistence.conversion.MapLinkConversionEventRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class MapLinkConversionEventServiceTest {
    @Test
    void recordsNaverProviderForFirstRequest() {
        var repository = mock(MapLinkConversionEventRepository.class);
        var writer = mock(MapLinkConversionEventWriter.class);
        var service = new MapLinkConversionEventService(repository, writer);
        when(repository.findByDeduplicationKey(anyString())).thenReturn(Optional.empty());
        when(writer.insert(any(MapLinkConversionEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, MapLinkConversionEvent.class));

        var event = service.record(1L, 2L, MapLinkConversionType.DIRECTIONS, "NAVER", "req-1", LocalDateTime.now());

        assertThat(event.getProvider()).isEqualTo("NAVER");
        verify(writer).insert(any(MapLinkConversionEvent.class));
    }

    @Test
    void preservesExistingKakaoEventForSequentialRetry() {
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
                "NAVER",
                "MAP_LINK:DIRECTIONS:1:2:req-1",
                LocalDateTime.now()
        );
        when(repository.findByDeduplicationKey(anyString()))
                .thenReturn(Optional.empty(), Optional.of(existing));
        when(writer.insert(any(MapLinkConversionEvent.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        var event = service.record(1L, 2L, MapLinkConversionType.DIRECTIONS, "NAVER", "req-1", LocalDateTime.now());

        assertThat(event).isSameAs(existing);
        verify(writer).insert(any(MapLinkConversionEvent.class));
    }

    @Test
    void differentRequestIdsRecordSeparateNaverEvents() {
        var repository = mock(MapLinkConversionEventRepository.class);
        var writer = mock(MapLinkConversionEventWriter.class);
        var service = new MapLinkConversionEventService(repository, writer);
        when(repository.findByDeduplicationKey(anyString())).thenReturn(Optional.empty());
        when(writer.insert(any(MapLinkConversionEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, MapLinkConversionEvent.class));

        service.record(1L, 2L, MapLinkConversionType.DIRECTIONS, "NAVER", "request-1", LocalDateTime.now());
        service.record(1L, 2L, MapLinkConversionType.DIRECTIONS, "NAVER", "request-2", LocalDateTime.now());

        ArgumentCaptor<MapLinkConversionEvent> captor = ArgumentCaptor.forClass(MapLinkConversionEvent.class);
        verify(writer, times(2)).insert(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(MapLinkConversionEvent::getProvider)
                .containsOnly("NAVER");
        assertThat(captor.getAllValues())
                .extracting(MapLinkConversionEvent::getDeduplicationKey)
                .containsExactly(
                        "MAP_LINK:DIRECTIONS:1:2:request-1",
                        "MAP_LINK:DIRECTIONS:1:2:request-2"
                );
    }
}
