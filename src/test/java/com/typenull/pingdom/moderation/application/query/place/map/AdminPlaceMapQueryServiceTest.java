package com.typenull.pingdom.moderation.application.query.place.map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.typenull.pingdom.moderation.api.dto.place.map.AdminPlaceMapRequest;
import com.typenull.pingdom.moderation.api.dto.place.map.AdminPlaceMapResponse.*;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import com.typenull.pingdom.place.infrastructure.persistence.place.AdminPlaceMapQueryRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.AdminPlaceMapQueryRepository.Cell;
import com.typenull.pingdom.place.infrastructure.persistence.place.AdminPlaceMapQueryRepository.GroupState;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;

class AdminPlaceMapQueryServiceTest {
    private final AdminPlaceMapQueryRepository repository = mock(AdminPlaceMapQueryRepository.class);
    private final AdminPlaceMapClusterToken tokens = mock(AdminPlaceMapClusterToken.class);
    private final AdminPlaceMapQueryService service = new AdminPlaceMapQueryService(repository, tokens, new AdminPlaceMapProperties(200));
    private final Bounds bounds = new Bounds(128.6, 35.87, 128.6, 35.87);

    private AdminPlaceMapRequest request(int zoom) {
        return new AdminPlaceMapRequest(128.5, 35.8, 128.7, 35.95, zoom, 1200, 800, null, null, null, null);
    }

    @BeforeEach
    void setUp() { when(repository.countViewport(any())).thenReturn(25L); }

    @Test
    void reclustersInsteadOfSilentlyTruncatingAndPreservesMixedResults() {
        var excess = java.util.Collections.nCopies(201, new Cell(1, 1, 2, 1, 35.87, 128.6, bounds, "a"));
        var group = new Cell(1, 1, 24, 1, 35.87, 128.6, bounds, "fingerprint");
        var single = new Cell(2, 2, 1, 25, 35.88, 128.61, bounds, "single");
        when(repository.findCells(any(), anyDouble(), eq(false), anyInt())).thenReturn(excess, List.of(group, single));
        when(tokens.issue(any(), anyDouble(), eq(1L), eq(1L), eq(false), eq("fingerprint"))).thenReturn("token");
        when(repository.markers(List.of(1L, 25L))).thenReturn(List.of(new Marker(1, "대표 카페", 35.87, 128.6, "CAFE", "HIDDEN", "OPERATING"), new Marker(25, "카페", 35.88, 128.61, "CAFE", "HIDDEN", "OPERATING")));
        var response = service.find(request(14));
        assertThat(response.complete()).isTrue();
        assertThat(response.clusters()).singleElement().satisfies(c -> {
            assertThat(c.count()).isEqualTo(24);
            assertThat(c.representative().id()).isEqualTo(1);
            assertThat(c.action()).isEqualTo("LIST");
        });
        assertThat(response.markers()).extracting(Marker::id).containsExactly(25L);
        assertThat(response.viewportPlaceCount()).isEqualTo(25);
        var sizes = org.mockito.ArgumentCaptor.forClass(Double.class);
        verify(repository, times(2)).findCells(any(), sizes.capture(), eq(false), anyInt());
        assertThat(sizes.getAllValues().get(1)).isEqualTo(sizes.getAllValues().get(0) * 2);
    }

    @Test
    void regionalTotalsAreNotAddedToViewportAndUnknownRegionsUseSpatialFallback() {
        var region = new Region("SIGUNGU", "27260", "수성구", 320, "REGION", 35.87, 128.6, bounds, "PLACE_EXTENT", "EXPAND", 12);
        when(repository.findRegions(any(), eq("SIGUNGU"), anyInt())).thenReturn(List.of(region));
        when(repository.findCells(any(), anyDouble(), eq(true), anyInt())).thenReturn(List.of());
        when(repository.markers(List.of())).thenReturn(List.of());
        var response = service.find(request(10));
        assertThat(response.regions()).containsExactly(region);
        assertThat(response.viewportPlaceCount()).isEqualTo(25);
        verify(repository).findCells(any(), anyDouble(), eq(true), anyInt());
    }

    @Test
    void boundedReaggregationReturnsExplicitFailureWithoutPartialArrays() {
        when(repository.findCells(any(), anyDouble(), anyBoolean(), anyInt())).thenReturn(
                java.util.Collections.nCopies(201, new Cell(1, 1, 2, 1, 35.87, 128.6, bounds, "a")));
        var response = service.find(request(20));
        assertThat(response.complete()).isFalse();
        assertThat(response.clusters()).isEmpty();
        assertThat(response.incompleteReason()).isEqualTo("RESULT_BUDGET_EXCEEDED");
        verify(repository, times(24)).findCells(any(), anyDouble(), anyBoolean(), anyInt());
        verifyNoInteractions(tokens);
    }

    @Test
    void changedGroupRejectsLatePageRequest() {
        var condition = AdminPlaceMapCondition.from(request(20));
        when(tokens.read("token")).thenReturn(new AdminPlaceMapClusterToken.Payload(1, 9999999999L, condition, 100, 1, 1, false, "old"));
        when(repository.groupState(condition, 100, 1, 1, false)).thenReturn(new GroupState(24, "changed"));
        assertThatThrownBy(() -> service.groupPlaces("token", 1, 20)).isInstanceOf(AdminException.class)
                .hasMessageContaining("변경");
        verify(repository, never()).groupPlaces(any(), anyDouble(), anyLong(), anyLong(), anyBoolean(), anyInt(), anyInt());
    }

    @Test
    void stableGroupUsesOriginalConditionAndIdPagination() {
        var condition = AdminPlaceMapCondition.from(request(20));
        when(tokens.read("token")).thenReturn(new AdminPlaceMapClusterToken.Payload(1, 9999999999L, condition, 100, 1, 1, false, "same"));
        when(repository.groupState(condition, 100, 1, 1, false)).thenReturn(new GroupState(24, "same"));
        when(repository.groupPlaces(condition, 100, 1, 1, false, 2, 20)).thenReturn(List.of());
        var result = service.groupPlaces("token", 2, 20);
        assertThat(result.totalPages()).isEqualTo(2);
        assertThat(result.hasNext()).isFalse();
        verify(repository).groupPlaces(condition, 100, 1, 1, false, 2, 20);
    }

    @Test
    void queryTimeoutUsesDomainError() {
        when(repository.countViewport(any())).thenThrow(new QueryTimeoutException("cancel"));
        assertThatThrownBy(() -> service.find(request(14))).isInstanceOf(AdminException.class).hasMessageContaining("초과");
    }

    @Test
    void emptyViewportKeepsModeAndReturnsEmptyArrays() {
        when(repository.countViewport(any())).thenReturn(0L);
        var result = service.find(request(7));
        assertThat(result.mode()).isEqualTo("SIDO");
        assertThat(result.regions()).isEmpty();
        assertThat(result.clusters()).isEmpty();
        assertThat(result.markers()).isEmpty();
        assertThat(result.complete()).isTrue();
    }
}
