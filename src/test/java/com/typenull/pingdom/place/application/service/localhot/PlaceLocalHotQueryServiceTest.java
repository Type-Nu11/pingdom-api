package com.typenull.pingdom.place.application.service.localhot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.place.api.dto.localhot.PlaceLocalHotQuery;
import com.typenull.pingdom.place.api.dto.localhot.PlaceLocalHotResponse;
import com.typenull.pingdom.place.domain.place.region.PlaceAdministrativeRegionResolver;
import com.typenull.pingdom.place.domain.place.region.ResolvedPlaceAdministrativeRegion;
import com.typenull.pingdom.place.infrastructure.persistence.place.PlaceAdministrativeRegionRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.PlaceLocalHotQueryRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

class PlaceLocalHotQueryServiceTest {

    @Test
    void 좌표로_법정동_시군구를_판정하고_페이지_순위를_유지한다() {
        PlaceAdministrativeRegionResolver regionResolver = mock(PlaceAdministrativeRegionResolver.class);
        PlaceAdministrativeRegionRepository regionRepository = mock(PlaceAdministrativeRegionRepository.class);
        PlaceLocalHotQueryRepository queryRepository = mock(PlaceLocalHotQueryRepository.class);
        ResolvedPlaceAdministrativeRegion region = new ResolvedPlaceAdministrativeRegion(
                "11680", "서울특별시", "강남구", "서울특별시 강남구"
        );
        when(regionResolver.resolve(37.5172d, 127.0473d)).thenReturn(region);
        when(queryRepository.countLocalHotPlaces("11680")).thenReturn(21L);
        when(queryRepository.findLocalHotPlaces(eq("11680"), eq(7L), any(Pageable.class)))
                .thenReturn(List.of(projection(11L, 12L, true)));
        PlaceLocalHotQueryService service = new PlaceLocalHotQueryService(
                regionResolver,
                regionRepository,
                queryRepository
        );

        PlaceLocalHotResponse response = service.find(
                new PlaceLocalHotQuery(37.5172d, 127.0473d, null, 2, 20),
                7L
        );

        assertThat(response.region().regionCode()).isEqualTo("11680");
        assertThat(response.region().regionName()).isEqualTo("서울특별시 강남구");
        assertThat(response.totalElements()).isEqualTo(21L);
        assertThat(response.totalPages()).isEqualTo(2);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.places()).singleElement().satisfies(item -> {
            assertThat(item.rank()).isEqualTo(21);
            assertThat(item.bookmarkCount()).isEqualTo(12L);
            assertThat(item.bookmarked()).isTrue();
        });
        verify(queryRepository).findLocalHotPlaces(eq("11680"), eq(7L), any(Pageable.class));
    }

    private PlaceLocalHotQueryRepository.PlaceLocalHotProjection projection(
            Long placeId,
            long bookmarkCount,
            boolean bookmarked
    ) {
        return new PlaceLocalHotQueryRepository.PlaceLocalHotProjection() {
            @Override public Long getPlaceId() { return placeId; }
            @Override public String getPlaceName() { return "강남 핫플"; }
            @Override public String getCategory() { return "카페"; }
            @Override public String getAddress() { return "서울특별시 강남구"; }
            @Override public Double getLatitude() { return 37.5172d; }
            @Override public Double getLongitude() { return 127.0473d; }
            @Override public String getImageUrl() { return "https://example.com/local-hot.jpg"; }
            @Override public long getBookmarkCount() { return bookmarkCount; }
            @Override public boolean getBookmarked() { return bookmarked; }
        };
    }
}
