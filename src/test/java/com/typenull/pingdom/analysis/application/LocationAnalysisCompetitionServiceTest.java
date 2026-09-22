package com.typenull.pingdom.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.analysis.application.ai.LocationAnalysisContent;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceCoordinateQueryRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

class LocationAnalysisCompetitionServiceTest {

    /**
     * 추천 좌표 반경 1,500m 조회 결과의 42.5m 거리 동일 업종 장소가 주변 경쟁업체로 반영되는지 검증.
     * 저장소 대역 사용으로 실제 거리 필터의 100m 제한은 검증 범위에서 제외.
     */
    @Test
    void enrichesNearbySameCategoryCompetitors() {
        MapPlaceCoordinateQueryRepository repository = mock(MapPlaceCoordinateQueryRepository.class);
        MapPlaceCoordinateQueryRepository.NearbyAnalysisPlace nearby = mock(
                MapPlaceCoordinateQueryRepository.NearbyAnalysisPlace.class
        );
        when(nearby.getPlaceId()).thenReturn(10L);
        when(nearby.getName()).thenReturn("동일 업종 매장");
        when(nearby.getCategory()).thenReturn("CAFE");
        when(nearby.getAddress()).thenReturn("서울 강남구 테헤란로 1");
        when(nearby.getDistanceMeters()).thenReturn(42.5d);
        when(repository.findNearbyPlacesForAnalysis(eq(35.1d), eq(128.1d), eq(1500d), any(Pageable.class)))
                .thenReturn(List.of(nearby));

        LocationAnalysisContent content = new LocationAnalysisContent(
                "보고서", null, null, null, null, null,
                new LocationAnalysisContent.CompetitionAnalysis(
                        "경쟁 환경", 0, 0, 0, 0d, List.of(), List.of()
                ), null, null,
                List.of(new LocationAnalysisContent.RecommendedPlace(
                        1, "추천 장소", "주소", 80d, "근거", List.of(), 35.1d, 128.1d
                )), null, List.of(), List.of()
        );

        LocationAnalysisContent enriched = new LocationAnalysisCompetitionService(repository)
                .enrich(content, "카페");

        assertThat(enriched.nearbyFacilities().competitors()).singleElement()
                .extracting(LocationAnalysisContent.Facility::name)
                .isEqualTo("동일 업종 매장");
        verify(repository).findNearbyPlacesForAnalysis(eq(35.1d), eq(128.1d), eq(1500d), any(Pageable.class));
    }

    /**
     * 한 주변 장소 조회 결과의 카페·역·쇼핑몰을 경쟁·교통·편의 시설로 분류하고 경쟁점 수 1과 요약을 반영하는지 검증.
     */
    @Test
    void classifiesNearbyFacilityTypes() {
        MapPlaceCoordinateQueryRepository repository = mock(MapPlaceCoordinateQueryRepository.class);
        List<MapPlaceCoordinateQueryRepository.NearbyAnalysisPlace> nearbyPlaces = List.of(
                nearby(1L, "잠실역 2호선", "OTHER", 120d),
                nearby(2L, "롯데월드몰", "FASHION", 250d),
                nearby(3L, "카페 경쟁점", "CAFE", 50d)
        );
        when(repository.findNearbyPlacesForAnalysis(eq(35.1d), eq(128.1d), eq(1500d), any(Pageable.class)))
                .thenReturn(nearbyPlaces);

        LocationAnalysisContent content = new LocationAnalysisContent(
                "보고서", null, null, null, null, null,
                new LocationAnalysisContent.CompetitionAnalysis(
                        "경쟁 환경", 0, 0, 0, 0d, List.of(), List.of()
                ), null, null,
                List.of(new LocationAnalysisContent.RecommendedPlace(
                        1, "추천 장소", "주소", 80d, "근거", List.of(), 35.1d, 128.1d
                )), null, List.of(), List.of()
        );

        LocationAnalysisContent enriched = new LocationAnalysisCompetitionService(repository)
                .enrich(content, "카페");

        assertThat(enriched.nearbyFacilities().competitors())
                .extracting(LocationAnalysisContent.Facility::name)
                .containsExactly("카페 경쟁점");
        assertThat(enriched.nearbyFacilities().transportFacilities())
                .extracting(LocationAnalysisContent.Facility::name)
                .containsExactly("잠실역 2호선");
        assertThat(enriched.nearbyFacilities().convenienceFacilities())
                .extracting(LocationAnalysisContent.Facility::name)
                .containsExactly("롯데월드몰");
        assertThat(enriched.competitionAnalysis().totalCompetitors()).isEqualTo(1);
        assertThat(enriched.competitionAnalysis().summary()).contains("경쟁점 1건");
    }

    /**
     * ID·명칭·분류·거리와 공통 주소를 반환하는 주변 장소 projection mock을 구성.
     */
    private MapPlaceCoordinateQueryRepository.NearbyAnalysisPlace nearby(
            long id, String name, String category, double distance
    ) {
        MapPlaceCoordinateQueryRepository.NearbyAnalysisPlace place = mock(
                MapPlaceCoordinateQueryRepository.NearbyAnalysisPlace.class
        );
        when(place.getPlaceId()).thenReturn(id);
        when(place.getName()).thenReturn(name);
        when(place.getCategory()).thenReturn(category);
        when(place.getAddress()).thenReturn("서울 송파구 잠실동");
        when(place.getDistanceMeters()).thenReturn(distance);
        return place;
    }
}
