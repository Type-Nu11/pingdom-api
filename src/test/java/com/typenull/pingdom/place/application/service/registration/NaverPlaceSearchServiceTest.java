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

    /**
     * 검색어 공백과 결과 제목 HTML을 제거하고 한 건의 네이버 정수 좌표를 도 단위 위경도로 변환하는지 확인합니다.
     */
    @Test
    void normalizesNaverPlaceResult() throws Exception {
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

    /**
     * 공백 검색어에 MapException이 발생하는지 확인합니다.
     */
    @Test
    void rejectsBlankSearchQuery() {
        assertThatThrownBy(() -> service.search("  ", 1L)).isInstanceOf(MapException.class);
    }
}
