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
    /**
     * 처음 요청한 지도 전환을 새로 저장하면서 NAVER 제공자 값을 보존하는지 확인.
     */
    @Test
    void recordsNaverProvider() {
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

    /**
     * 같은 중복 키의 재요청은 기존 KAKAO 이벤트를 그대로 반환하고 writer를 호출하지 않는지 확인.
     */
    @Test
    void reusesExistingKakaoEvent() {
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

    /**
     * 삽입의 유일 제약 실패를 모의한 뒤 재조회한 기존 이벤트를 반환하는지 확인. 실제 동시 스레드 실행과 DB 충돌은 검증 범위에서 제외.
     */
    @Test
    void reloadsAfterDuplicateInsert() {
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

    /**
     * 사용자·장소·유형이 같아도 requestId가 다르면 NAVER 이벤트 두 건과 서로 다른 중복 키를 저장하는지 확인.
     */
    @Test
    void separatesDistinctRequestIds() {
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
