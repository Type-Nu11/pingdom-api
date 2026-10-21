package com.typenull.pingdom.moderation.application.query.place.map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.typenull.pingdom.moderation.api.dto.place.map.AdminPlaceMapRequest;
import com.typenull.pingdom.moderation.api.dto.place.map.AdminPlaceMapResponse.DisplayStatus;
import com.typenull.pingdom.moderation.api.dto.place.map.AdminPlaceMapResponse.Marker;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import com.typenull.pingdom.place.domain.place.discovery.PlaceDiscoveryStatus;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingStatus;
import com.typenull.pingdom.place.infrastructure.persistence.place.AdminPlaceMapQueryRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.AdminPlaceMapQueryRepository.GroupState;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;

class AdminPlaceMapQueryServiceTest {
    private final AdminPlaceMapQueryRepository repository = mock(AdminPlaceMapQueryRepository.class);
    private final AdminPlaceMapClusterToken tokens = mock(AdminPlaceMapClusterToken.class);
    private final AdminPlaceMapQueryService service = new AdminPlaceMapQueryService(repository, tokens, new AdminPlaceMapProperties(100));

    private AdminPlaceMapRequest request(int zoom) {
        return new AdminPlaceMapRequest(128.5, 35.8, 128.7, 35.95, zoom, 1200, 800, null, null, null, null);
    }

    @ParameterizedTest
    @CsvSource({"0,0", "0,1", "0,99", "0,100", "7,100", "10,100", "13,100",
            "20,0", "20,1", "20,99", "20,100"})
    void returnsEveryMarkerAtOrBelowLimitRegardlessOfZoom(int zoom, int count) {
        var condition = AdminPlaceMapCondition.from(request(zoom));
        var markers = IntStream.rangeClosed(1, count)
                .mapToObj(id -> new Marker(id, "카페", 35.87, 128.6, "CAFE", "HIDDEN", "OPERATING")).toList();
        when(repository.countViewport(condition)).thenReturn((long) count);
        when(repository.findViewportMarkers(condition)).thenReturn(markers);

        var result = service.find(request(zoom));

        assertThat(result.mode()).isEqualTo("SPATIAL");
        assertThat(result.zoom()).isEqualTo(zoom);
        assertThat(result.displayStatus()).isEqualTo(DisplayStatus.MARKERS);
        assertThat(result.displayLimit()).isEqualTo(100);
        assertThat(result.viewportPlaceCount()).isEqualTo(count);
        assertThat(result.markers()).containsExactlyElementsOf(markers);
        assertThat(result.markers()).extracting(Marker::id).doesNotHaveDuplicates();
        assertThat(result.regions()).isEmpty();
        assertThat(result.clusters()).isEmpty();
        assertThat(result.complete()).isTrue();
        assertThat(result.incompleteReason()).isNull();
        assertThat(result.recoveryAction()).isNull();
        verify(repository).setQueryTimeout();
        verify(repository).countViewport(condition);
        if (count > 0) verify(repository).findViewportMarkers(condition);
        verifyNoMoreInteractions(repository);
        verifyNoInteractions(tokens);
    }

    @ParameterizedTest
    @CsvSource({"0,101", "7,101", "10,101", "13,101", "20,101", "0,10000", "20,10000"})
    void returnsExactCountAndZoomHintWithoutLoadingMarkersOrGroups(int zoom, long count) {
        var condition = AdminPlaceMapCondition.from(request(zoom));
        when(repository.countViewport(condition)).thenReturn(count);

        var result = service.find(request(zoom));

        assertThat(result.displayStatus()).isEqualTo(DisplayStatus.ZOOM_REQUIRED);
        assertThat(result.displayLimit()).isEqualTo(100);
        assertThat(result.viewportPlaceCount()).isEqualTo(count);
        assertThat(result.markers()).isEmpty();
        assertThat(result.regions()).isEmpty();
        assertThat(result.clusters()).isEmpty();
        assertThat(result.complete()).isFalse();
        assertThat(result.incompleteReason()).isEqualTo("RESULT_BUDGET_EXCEEDED");
        assertThat(result.recoveryAction()).isEqualTo("ZOOM_IN");
        verify(repository).setQueryTimeout();
        verify(repository).countViewport(condition);
        verifyNoMoreInteractions(repository);
        verifyNoInteractions(tokens);
    }

    @Test
    void preservesAllFiltersAndIgnoresCssSizeForDisplayDecision() {
        var request = new AdminPlaceMapRequest(128.5, 35.8, 128.7, 35.95, 0, 64, 64,
                " 카페 ", "커피", PlaceDiscoveryStatus.HIDDEN, PlaceOperatingStatus.PERMANENTLY_CLOSED);
        var condition = AdminPlaceMapCondition.from(request);
        when(repository.countViewport(condition)).thenReturn(100L);
        var markers = IntStream.rangeClosed(1, 100)
                .mapToObj(id -> new Marker(id, "카페", 35.87, 128.6, "CAFE", "HIDDEN", "PERMANENTLY_CLOSED")).toList();
        when(repository.findViewportMarkers(condition)).thenReturn(markers);

        var result = service.find(request);

        assertThat(result.markers()).hasSize(100);
        assertThat(condition.keyword()).isEqualTo("카페");
        assertThat(condition.category()).isEqualTo("CAFE");
        var order = inOrder(repository);
        order.verify(repository).setQueryTimeout();
        order.verify(repository).countViewport(condition);
        order.verify(repository).findViewportMarkers(condition);
        verifyNoMoreInteractions(repository);
        verifyNoInteractions(tokens);
    }

    @Test
    void reportsAndAppliesTheServerConfiguredLimit() {
        var limited = new AdminPlaceMapQueryService(repository, tokens, new AdminPlaceMapProperties(80));
        when(repository.countViewport(any())).thenReturn(81L);

        var result = limited.find(request(20));

        assertThat(result.displayLimit()).isEqualTo(80);
        assertThat(result.displayStatus()).isEqualTo(DisplayStatus.ZOOM_REQUIRED);
        verify(repository, never()).findViewportMarkers(any());
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
    void countTimeoutUsesDomainError() {
        when(repository.countViewport(any())).thenThrow(new QueryTimeoutException("cancel"));
        assertThatThrownBy(() -> service.find(request(14))).isInstanceOfSatisfying(AdminException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(AdminErrorCode.ADMIN_PLACE_MAP_QUERY_TIMEOUT));
        verify(repository, never()).findViewportMarkers(any());
    }

    @Test
    void markerQueryCancellationUsesDomainError() {
        when(repository.countViewport(any())).thenReturn(1L);
        when(repository.findViewportMarkers(any())).thenThrow(new DataAccessResourceFailureException(
                "cancel", new SQLException("statement timeout", "57014")));
        assertThatThrownBy(() -> service.find(request(14))).isInstanceOfSatisfying(AdminException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(AdminErrorCode.ADMIN_PLACE_MAP_QUERY_TIMEOUT));
    }
}
