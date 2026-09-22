package com.typenull.pingdom.place.application.service.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.place.api.dto.place.map.MapClusterItem;
import com.typenull.pingdom.place.api.dto.place.map.MapMarkerItem;
import com.typenull.pingdom.place.api.dto.place.map.MapViewportResponse;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapViewportQueryRepository;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MapViewportQueryServiceTest {

    @Mock
    private MapViewportQueryRepository repository;

    @InjectMocks
    private MapViewportQueryService service;

    /**
     * zoom 13에서는 클러스터만 조회하고 마커 목록은 비우며 잘림 표시가 없는지 확인합니다.
     */
    @Test
    void loadsClustersAtLowZoom() {
        MapClusterItem cluster = new MapClusterItem("10:20", 35.18, 128.10, 12);
        when(repository.findClusters(eq(128.0), eq(35.1), eq(128.2), eq(35.3), anyDouble(), eq(501)))
                .thenReturn(List.of(cluster));

        MapViewportResponse response = service.find(128.0, 35.1, 128.2, 35.3, 13);

        assertThat(response.mode()).isEqualTo("CLUSTERS");
        assertThat(response.clusters()).containsExactly(cluster);
        assertThat(response.markers()).isEmpty();
        assertThat(response.truncated()).isFalse();
        verify(repository, never()).findMarkers(anyDouble(), anyDouble(), anyDouble(), anyDouble(), eq(501));
    }

    /**
     * zoom 14에서 501개 조회 결과를 마커 500개와 truncated=true로 반환하고 클러스터 조회를 생략하는지 확인합니다.
     */
    @Test
    void capsHighZoomMarkers() {
        List<MapMarkerItem> markers = new ArrayList<>();
        for (int index = 0; index < 501; index++) {
            markers.add(new MapMarkerItem(index + 1L, "장소", "카페", null, 35.18, 128.10, 0));
        }
        when(repository.findMarkers(128.0, 35.1, 128.2, 35.3, 501)).thenReturn(markers);

        MapViewportResponse response = service.find(128.0, 35.1, 128.2, 35.3, 14);

        assertThat(response.mode()).isEqualTo("MARKERS");
        assertThat(response.markers()).hasSize(500);
        assertThat(response.clusters()).isEmpty();
        assertThat(response.truncated()).isTrue();
        verify(repository, never()).findClusters(
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), eq(501)
        );
    }

    /**
     * 서쪽 경도가 동쪽보다 큰 요청은 검색 조건 오류로 거절하며 저장소를 조회하지 않는지 확인합니다.
     */
    @Test
    void rejectsInvalidViewport() {
        assertThatThrownBy(() -> service.find(128.2, 35.1, 128.0, 35.3, 14))
                .isInstanceOfSatisfying(MapException.class, exception ->
                        assertThat(exception.getErrorCode())
                                .isEqualTo(MapErrorCode.PLACE_SEARCH_CONDITION_INVALID));

        verify(repository, never()).findMarkers(anyDouble(), anyDouble(), anyDouble(), anyDouble(), eq(501));
        verify(repository, never()).findClusters(
                anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), eq(501)
        );
    }
}
