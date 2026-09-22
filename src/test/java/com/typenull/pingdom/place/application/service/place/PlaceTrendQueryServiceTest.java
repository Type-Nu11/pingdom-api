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

    /**
     * 요청 기간이 이력 수집 시작보다 이르면 시작을 수집 시각으로 보정하고 두 번째 페이지 순위·순증·북마크 정보를 유지하는지 확인합니다.
     */
    @Test
    void clampsTrendTrackingWindow() {
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

    /**
     * 추가·해제·순증·현재 북마크 수를 지정하는 트렌드 조회 행을 만듭니다.
     */
    private PlaceTrendQueryRepository.PlaceTrendProjection projection(
            Long placeId,
            long bookmarkAdds,
            long bookmarkRemoves,
            long netBookmarkGrowth,
            long bookmarkCount,
            boolean bookmarked
    ) {
        return new PlaceTrendQueryRepository.PlaceTrendProjection() {
            /** 트렌드 응답 행의 장소 ID를 제공한다. */
            @Override public Long getPlaceId() { return placeId; }
            /** 트렌드 응답에 표시할 고정 장소명을 제공한다. */
            @Override public String getPlaceName() { return "트렌드 장소"; }
            /** 응답 매핑에 사용할 카페 카테고리를 제공한다. */
            @Override public String getCategory() { return "카페"; }
            /** 응답 매핑에 사용할 대표 이미지 URL을 제공한다. */
            @Override public String getImageUrl() { return "https://example.com/trend.jpg"; }
            /** 응답 매핑에 사용할 서울 주소를 제공한다. */
            @Override public String getAddress() { return "서울특별시"; }
            /** 기간 내 북마크 추가 집계값을 제공한다. */
            @Override public long getBookmarkAdds() { return bookmarkAdds; }
            /** 기간 내 북마크 해제 집계값을 제공한다. */
            @Override public long getBookmarkRemoves() { return bookmarkRemoves; }
            /** 추가에서 해제를 뺀 순증 집계값을 제공한다. */
            @Override public long getNetBookmarkGrowth() { return netBookmarkGrowth; }
            /** 현재 누적 북마크 수를 제공한다. */
            @Override public long getBookmarkCount() { return bookmarkCount; }
            /** 조회 사용자 본인의 북마크 여부를 제공한다. */
            @Override public boolean getBookmarked() { return bookmarked; }
        };
    }
}
