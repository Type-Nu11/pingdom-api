package com.typenull.pingdom.place.application.service.localhot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.place.api.dto.localhot.PlaceLocalHotQuery;
import com.typenull.pingdom.place.api.dto.localhot.PlaceLocalHotResponse;
import com.typenull.pingdom.place.domain.place.region.PlaceAdministrativeRegion;
import com.typenull.pingdom.place.domain.place.region.PlaceAdministrativeRegionResolver;
import com.typenull.pingdom.place.domain.place.region.ResolvedPlaceAdministrativeRegion;
import com.typenull.pingdom.place.infrastructure.persistence.place.PlaceAdministrativeRegionRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.PlaceLocalHotQueryRepository;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

class PlaceLocalHotQueryServiceTest {

    /**
     * 지역 장소가 없으면 지역 정보와 빈 목록·총 0건·총 1페이지·다음 없음으로 응답하는지 확인합니다.
     */
    @Test
    void returnsEmptyRegionalList() {
        PlaceAdministrativeRegionResolver regionResolver = mock(PlaceAdministrativeRegionResolver.class);
        PlaceAdministrativeRegionRepository regionRepository = mock(PlaceAdministrativeRegionRepository.class);
        PlaceLocalHotQueryRepository queryRepository = mock(PlaceLocalHotQueryRepository.class);
        when(regionResolver.resolve(37.5172d, 127.0473d)).thenReturn(
                new ResolvedPlaceAdministrativeRegion("11680", "서울특별시", "강남구", "서울특별시 강남구"));
        when(queryRepository.countLocalHotPlaces("11680")).thenReturn(0L);
        when(queryRepository.findLocalHotPlaces(eq("11680"), eq(7L), any(Pageable.class)))
                .thenReturn(List.of());
        PlaceLocalHotQueryService service = new PlaceLocalHotQueryService(
                regionResolver, regionRepository, queryRepository);

        PlaceLocalHotResponse response = service.find(
                new PlaceLocalHotQuery(37.5172d, 127.0473d, null, 1, 20), 7L);

        assertThat(response.region().regionCode()).isEqualTo("11680");
        assertThat(response.places()).isEmpty();
        assertThat(response.totalElements()).isZero();
        assertThat(response.totalPages()).isEqualTo(1);
        assertThat(response.hasNext()).isFalse();
    }

    /**
     * 좌표 해석 결과를 조회에 사용하고 두 번째 페이지 첫 항목을 21위로 매핑하며 북마크 정보와 페이지 메타데이터를 유지하는지 확인합니다.
     */
    @Test
    void preservesRegionalPageRanks() {
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

    /**
     * 지역 코드를 직접 전달하면 저장 지역을 사용하고 외부 resolver를 호출하지 않는지 확인합니다.
     */
    @Test
    void bypassesResolverForRegionCode() {
        PlaceAdministrativeRegionResolver regionResolver = mock(PlaceAdministrativeRegionResolver.class);
        PlaceAdministrativeRegionRepository regionRepository = mock(PlaceAdministrativeRegionRepository.class);
        PlaceLocalHotQueryRepository queryRepository = mock(PlaceLocalHotQueryRepository.class);
        PlaceAdministrativeRegion region = mock(PlaceAdministrativeRegion.class);
        when(region.getCode()).thenReturn("11680");
        when(region.getSido()).thenReturn("서울특별시");
        when(region.getSigungu()).thenReturn("강남구");
        when(region.getRegionName()).thenReturn("서울특별시 강남구");
        when(regionRepository.findById("11680")).thenReturn(Optional.of(region));
        when(queryRepository.countLocalHotPlaces("11680")).thenReturn(0L);
        when(queryRepository.findLocalHotPlaces(eq("11680"), eq(7L), any(Pageable.class)))
                .thenReturn(List.of());
        PlaceLocalHotQueryService service = new PlaceLocalHotQueryService(
                regionResolver, regionRepository, queryRepository);

        PlaceLocalHotResponse response = service.find(new PlaceLocalHotQuery(null, null, "11680", 1, 20), 7L);

        assertThat(response.region().regionCode()).isEqualTo("11680");
        verifyNoInteractions(regionResolver);
    }

    /**
     * 좌표 해석 실패를 그대로 전파하고 지역·핫플 조회를 진행하지 않는지 확인합니다.
     */
    @Test
    void propagatesRegionResolutionFailure() {
        PlaceAdministrativeRegionResolver regionResolver = mock(PlaceAdministrativeRegionResolver.class);
        PlaceAdministrativeRegionRepository regionRepository = mock(PlaceAdministrativeRegionRepository.class);
        PlaceLocalHotQueryRepository queryRepository = mock(PlaceLocalHotQueryRepository.class);
        MapException resolutionFailure = new MapException(MapErrorCode.LOCAL_HOT_REGION_RESOLUTION_FAILED);
        when(regionResolver.resolve(37.5172d, 127.0473d)).thenThrow(resolutionFailure);
        PlaceLocalHotQueryService service = new PlaceLocalHotQueryService(
                regionResolver, regionRepository, queryRepository);

        assertThatThrownBy(() -> service.find(new PlaceLocalHotQuery(37.5172d, 127.0473d, null, 1, 20), 7L))
                .isSameAs(resolutionFailure);

        verifyNoInteractions(regionRepository, queryRepository);
    }

    /**
     * 페이지 순위와 북마크 응답을 확인할 핫플 조회 행을 만듭니다.
     */
    private PlaceLocalHotQueryRepository.PlaceLocalHotProjection projection(
            Long placeId,
            long bookmarkCount,
            boolean bookmarked
    ) {
        return new PlaceLocalHotQueryRepository.PlaceLocalHotProjection() {
            /** 지역 핫플 응답의 장소 ID를 제공한다. */
            @Override public Long getPlaceId() { return placeId; }
            /** 핫플 응답에 표시할 고정 장소명을 제공한다. */
            @Override public String getPlaceName() { return "강남 핫플"; }
            /** 응답 매핑에 사용할 카페 카테고리를 제공한다. */
            @Override public String getCategory() { return "카페"; }
            /** 강남 지역 후보의 표시 주소를 제공한다. */
            @Override public String getAddress() { return "서울특별시 강남구"; }
            /** 강남 지역 후보의 위도를 제공한다. */
            @Override public Double getLatitude() { return 37.5172d; }
            /** 강남 지역 후보의 경도를 제공한다. */
            @Override public Double getLongitude() { return 127.0473d; }
            /** 응답 매핑에 사용할 핫플 대표 이미지 URL을 제공한다. */
            @Override public String getImageUrl() { return "https://example.com/local-hot.jpg"; }
            /** 순위 응답에 포함할 현재 북마크 수를 제공한다. */
            @Override public long getBookmarkCount() { return bookmarkCount; }
            /** 조회 사용자 본인의 북마크 상태를 제공한다. */
            @Override public boolean getBookmarked() { return bookmarked; }
        };
    }
}
