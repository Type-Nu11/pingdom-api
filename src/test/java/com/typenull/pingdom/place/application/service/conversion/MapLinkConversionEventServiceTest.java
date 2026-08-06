package com.typenull.pingdom.place.application.service.conversion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import com.typenull.pingdom.place.domain.conversion.*;
import com.typenull.pingdom.place.infrastructure.persistence.conversion.MapLinkConversionEventRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MapLinkConversionEventServiceTest {
    @Test
    void recordsEachRequestIdOnlyOnce() {
        var repository = mock(MapLinkConversionEventRepository.class);
        var service = new MapLinkConversionEventService(repository);
        when(repository.findByDeduplicationKey(anyString())).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var event = service.record(1L, 2L, MapLinkConversionType.DIRECTIONS, "KAKAO", "req-1", LocalDateTime.now());
        assertThat(event.getProvider()).isEqualTo("KAKAO");
        verify(repository).save(any(MapLinkConversionEvent.class));
    }
}
