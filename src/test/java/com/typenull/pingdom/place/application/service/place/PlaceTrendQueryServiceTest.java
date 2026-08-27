package com.typenull.pingdom.place.application.service.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
import org.springframework.data.domain.Pageable;

class PlaceTrendQueryServiceTest {

    @Test
    void 이력_수집_시작_전_기간은_기준_시점부터_집계하고_페이지_순위를_유지한다() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 27, 12, 0);
        LocalDateTime trackingStartedAt = now.minusDays(2);
        PlaceTrendQueryRepository queryRepository = mock(PlaceTrendQueryRepository.class);
        MapBookmarkTrendTrackingRepository trackingRepository = mock(MapBookmarkTrendTrackingRepository.class);
        MapBookmarkTrendTracking tracking = mock(MapBookmarkTrendTracking.class);
        when(tracking.getStartedAt()).thenReturn(trackingStartedAt);
        when(trackingRepository.findById(Boolean.TRUE)).thenReturn(Optional.of(tracking));
        when(queryRepository.countTrends(trackingStartedAt, now)).thenReturn(21L);
        when(queryRepository.findTrends(eq(trackingStartedAt), eq(now), eq(7L), any(Pageable.class)))
                .thenReturn(List.of(projection(11L, 4L, 1L, 3L, 12L, true)));

        PlaceTrendQueryService service = new PlaceTrendQueryService(
                queryRepository,
                trackingRepository,
                Clock.fixed(Instant.parse("2026-08-27T12:00:00Z"), ZoneOffset.UTC)
        );

        PlaceTrendResponse response = service.find(PlaceTrendPeriod.WEEK, 2, 20, 7L);

        assertThat(response.periodStart()).isEqualTo(trackingStartedAt);
        assertThat(response.periodEnd()).isEqualTo(now);
        assertThat(response.generatedAt()).isEqualTo(now);
        assertThat(response.totalElements()).isEqualTo(21L);
        assertThat(response.totalPages()).isEqualTo(2);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.places()).singleElement().satisfies(item -> {
            assertThat(item.rank()).isEqualTo(21);
            assertThat(item.netBookmarkGrowth()).isEqualTo(3L);
            assertThat(item.bookmarked()).isTrue();
        });
        verify(queryRepository).findTrends(eq(trackingStartedAt), eq(now), eq(7L), any(Pageable.class));
    }

    private PlaceTrendQueryRepository.PlaceTrendProjection projection(
            Long placeId,
            long bookmarkAdds,
            long bookmarkRemoves,
            long netBookmarkGrowth,
            long bookmarkCount,
            boolean bookmarked
    ) {
        return new PlaceTrendQueryRepository.PlaceTrendProjection() {
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
