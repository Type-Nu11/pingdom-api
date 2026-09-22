package com.typenull.pingdom.place.domain.conversion;

import static org.assertj.core.api.Assertions.*;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class MapLinkConversionEventTest {
    /** 길찾기 전환을 생성하면 DIRECTIONS 유형과 KAKAO provider가 저장되는지 확인. 중복 키 값은 직접 검증 대상에서 제외. */
    @Test
    void createsDirectionsProviderEvent() {
        var event = MapLinkConversionEvent.create(1L, 2L, MapLinkConversionType.DIRECTIONS,
                "KAKAO", "MAP_LINK:DIRECTIONS:1:2", LocalDateTime.now());
        assertThat(event.getLinkType()).isEqualTo(MapLinkConversionType.DIRECTIONS);
        assertThat(event.getProvider()).isEqualTo("KAKAO");
    }

    /** 장소 식별자가 0인 지도 링크 전환 생성을 IllegalArgumentException으로 거부하는지 확인. */
    @Test
    void rejectsInvalidIdentifiers() {
        assertThatThrownBy(() -> MapLinkConversionEvent.create(0L, 2L, MapLinkConversionType.EXTERNAL_MAP,
                "GOOGLE", "key", LocalDateTime.now())).isInstanceOf(IllegalArgumentException.class);
    }
}
