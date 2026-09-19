package com.typenull.pingdom.place.application.service.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.shared.exception.MapException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NaverPlaceSearchServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock private NaverPlaceSearchClient client;
    @InjectMocks private NaverPlaceSearchService service;

    @Test
    void 네이버_업체명_검색_결과를_최대_다섯건의_WGS84_좌표로_변환한다() throws Exception {
        when(client.search("핑덤 카페")).thenReturn(objectMapper.readTree("""
                [{"title":"<b>핑덤</b> 카페","roadAddress":"서울시 도로명","address":"서울시 지번",
                  "mapx":"1271234567","mapy":"371234567"}]
                """));

        var response = service.search("  핑덤 카페  ", 1L);

        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.name()).isEqualTo("핑덤 카페");
            assertThat(item.roadAddress()).isEqualTo("서울시 도로명");
            assertThat(item.jibunAddress()).isEqualTo("서울시 지번");
            assertThat(item.latitude()).isEqualTo(37.1234567d);
            assertThat(item.longitude()).isEqualTo(127.1234567d);
        });
    }

    @Test
    void 빈_검색어는_외부_호출_전에_거부한다() {
        assertThatThrownBy(() -> service.search("  ", 1L)).isInstanceOf(MapException.class);
    }
}
