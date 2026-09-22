package com.typenull.pingdom.place.application.service.registration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NaverAddressSearchServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private NaverAddressSearchClient client;

    @InjectMocks
    private NaverAddressSearchService service;

    /**
     * Naver Geocoding의 x·y 좌표를 WGS84 경도·위도로 변환하고, POSTAL_CODE 구성 요소가 있으면 우편번호로 반환하는지 검증한다.
     */
    @Test
    void mapsAddressResult() throws Exception {
        when(client.search("분당구 불정로 6")).thenReturn(objectMapper.readTree("""
                [{"roadAddress":"경기도 성남시 분당구 불정로 6","jibunAddress":"경기도 성남시 분당구 정자동 178-1",
                  "addressElements":[{"types":["POSTAL_CODE"],"longName":"13561"}],
                  "x":"127.1054328","y":"37.3595963"}]
                """));

        var response = service.search("  분당구 불정로 6  ");

        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.roadAddress()).isEqualTo("경기도 성남시 분당구 불정로 6");
            assertThat(item.jibunAddress()).isEqualTo("경기도 성남시 분당구 정자동 178-1");
            assertThat(item.postalCode()).isEqualTo("13561");
            assertThat(item.latitude()).isEqualTo(37.3595963d);
            assertThat(item.longitude()).isEqualTo(127.1054328d);
        });
    }

    /**
     * Geocoding이 주소 후보를 반환하지 않으면 장소 생성이나 외부 호출 재시도 없이 빈 목록을 그대로 반환하는지 검증한다.
     */
    @Test
    void returnsEmptyResults() throws Exception {
        when(client.search("존재하지 않는 주소")).thenReturn(objectMapper.readTree("[]"));

        assertThat(service.search("존재하지 않는 주소").items()).isEmpty();
    }

    /**
     * 공백 검색어는 외부 Geocoding 호출 전에 잘못된 장소 검색 조건으로 거부하는지 검증한다.
     */
    @Test
    void rejectsBlankQuery() {
        assertThatThrownBy(() -> service.search("  "))
                .isInstanceOfSatisfying(MapException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(MapErrorCode.PLACE_SEARCH_CONDITION_INVALID));

        verifyNoInteractions(client);
    }
}
