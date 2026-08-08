package com.typenull.pingdom.place.domain.conversion;

import static org.assertj.core.api.Assertions.*;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class MapLinkConversionEventTest {
    @Test
    void createsDirectionsEventWithProviderAndDeduplicationKey() {
        var event = MapLinkConversionEvent.create(1L, 2L, MapLinkConversionType.DIRECTIONS,
                "KAKAO", "MAP_LINK:DIRECTIONS:1:2", LocalDateTime.now());
        assertThat(event.getLinkType()).isEqualTo(MapLinkConversionType.DIRECTIONS);
        assertThat(event.getProvider()).isEqualTo("KAKAO");
    }

    @Test
    void rejectsInvalidIdentifiers() {
        assertThatThrownBy(() -> MapLinkConversionEvent.create(0L, 2L, MapLinkConversionType.EXTERNAL_MAP,
                "GOOGLE", "key", LocalDateTime.now())).isInstanceOf(IllegalArgumentException.class);
    }
}
