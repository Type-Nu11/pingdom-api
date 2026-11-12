package com.typenull.pingdom.place.application.service.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.place.api.dto.trend.PlaceTrendPeriod;
import com.typenull.pingdom.place.api.dto.trend.PlaceTrendResponse;
import com.typenull.pingdom.place.domain.place.core.MapBookmarkTrendTracking;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapBookmarkTrendTrackingRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.PlaceTrendQueryRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PlaceTrendQueryServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 27, 12, 0);
    private final PlaceTrendQueryRepository queryRepository = mock(PlaceTrendQueryRepository.class);
    private final MapBookmarkTrendTrackingRepository trackingRepository = mock(MapBookmarkTrendTrackingRepository.class);

    @Test
    void clampsTrendTrackingWindow() {
        LocalDateTime trackingStartedAt = NOW.minusDays(2);
        PlaceTrendQueryService service = service(trackingStartedAt);
        var row = projection(21L, 11L, 4L, 1L, 3L, 12L, true);
        when(queryRepository.findTrendPage(trackingStartedAt, NOW, 7L, 20L, 20))
                .thenReturn(List.of(row));

        PlaceTrendResponse response = service.find(PlaceTrendPeriod.WEEK, 2, 20, 7L);

        assertThat(response.periodStart()).isEqualTo(trackingStartedAt);
        assertThat(response.periodEnd()).isEqualTo(NOW);
        assertThat(response.generatedAt()).isEqualTo(NOW);
        assertThat(response.totalElements()).isEqualTo(21L);
        assertThat(response.totalPages()).isEqualTo(2);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.places()).singleElement().satisfies(item -> {
            assertThat(item.rank()).isEqualTo(21);
            assertThat(item.netBookmarkGrowth()).isEqualTo(3L);
            assertThat(item.bookmarked()).isTrue();
        });
        verify(queryRepository).findTrendPage(trackingStartedAt, NOW, 7L, 20L, 20);
        verifyNoMoreInteractions(queryRepository);
    }

    @Test
    void retainsZeroTotalWithoutReturningTheMetadataRow() {
        PlaceTrendQueryService service = service(NOW.minusDays(30));
        var metadata = projection(0L, null, 0L, 0L, 0L, 0L, false);
        when(queryRepository.findTrendPage(NOW.minusDays(7), NOW, 7L, 0L, 1))
                .thenReturn(List.of(metadata));

        PlaceTrendResponse response = service.find(null, 0, 0, 7L);

        assertThat(response.period()).isEqualTo(PlaceTrendPeriod.WEEK);
        assertThat(response.periodStart()).isEqualTo(NOW.minusDays(7));
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.limit()).isEqualTo(1);
        assertThat(response.places()).isEmpty();
        assertThat(response.totalElements()).isZero();
        assertThat(response.totalPages()).isEqualTo(1);
        assertThat(response.hasNext()).isFalse();
    }

    @Test
    void retainsTotalForAnOutOfRangePage() {
        PlaceTrendQueryService service = service(NOW.minusDays(30));
        var metadata = projection(21L, null, 0L, 0L, 0L, 0L, false);
        when(queryRepository.findTrendPage(NOW.minusDays(7), NOW, 7L, 40L, 20))
                .thenReturn(List.of(metadata));

        PlaceTrendResponse response = service.find(PlaceTrendPeriod.WEEK, 3, 20, 7L);

        assertThat(response.places()).isEmpty();
        assertThat(response.totalElements()).isEqualTo(21L);
        assertThat(response.page()).isEqualTo(3);
        assertThat(response.totalPages()).isEqualTo(2);
        assertThat(response.hasNext()).isFalse();
    }

    @Test
    void clampsLimitAndPreservesPageOrderAndRanks() {
        PlaceTrendQueryService service = service(NOW.minusDays(30));
        var first = projection(51L, 13L, 4L, 1L, 3L, 12L, false);
        var second = projection(51L, 11L, 4L, 1L, 3L, 12L, true);
        when(queryRepository.findTrendPage(NOW.minusDays(7), NOW, 7L, 0L, 50))
                .thenReturn(List.of(first, second));

        PlaceTrendResponse response = service.find(PlaceTrendPeriod.WEEK, 1, 100, 7L);

        assertThat(response.limit()).isEqualTo(50);
        assertThat(response.totalPages()).isEqualTo(2);
        assertThat(response.hasNext()).isTrue();
        assertThat(response.places()).extracting(PlaceTrendResponse.Item::placeId)
                .containsExactly(13L, 11L);
        assertThat(response.places()).extracting(PlaceTrendResponse.Item::rank)
                .containsExactly(1, 2);
    }

    private PlaceTrendQueryService service(LocalDateTime trackingStartedAt) {
        MapBookmarkTrendTracking tracking = mock(MapBookmarkTrendTracking.class);
        when(tracking.getStartedAt()).thenReturn(trackingStartedAt);
        when(trackingRepository.findById(Boolean.TRUE)).thenReturn(Optional.of(tracking));
        return new PlaceTrendQueryService(
                queryRepository,
                trackingRepository,
                Clock.fixed(Instant.parse("2026-08-27T12:00:00Z"), ZoneOffset.UTC)
        );
    }

    private PlaceTrendQueryRepository.PlaceTrendProjection projection(
            long totalElements,
            Long placeId,
            long bookmarkAdds,
            long bookmarkRemoves,
            long netBookmarkGrowth,
            long bookmarkCount,
            boolean bookmarked
    ) {
        return new PlaceTrendQueryRepository.PlaceTrendProjection() {
            @Override public long getTotalElements() { return totalElements; }
            @Override public Long getPlaceId() { return placeId; }
            @Override public String getPlaceName() { return "트렌드 장소"; }
            @Override public String getCategory() { return "카페"; }
            @Override public String getImageUrl() { return "https://example.com/trend.jpg"; }
            @Override public String getAddress() { return "서울특별시"; }
            @Override public long getBookmarkAdds() { return bookmarkAdds; }
            @Override public long getBookmarkRemoves() { return bookmarkRemoves; }
            @Override public long getNetBookmarkGrowth() { return netBookmarkGrowth; }
            @Override public long getBookmarkCount() { return bookmarkCount; }
            @Override public boolean getBookmarked() { return bookmarked; }
        };
    }
}
