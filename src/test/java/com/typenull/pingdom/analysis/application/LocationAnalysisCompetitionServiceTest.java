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

    @Test
    void enrichesReportWithSameCategoryPlacesWithinOneHundredMeters() {
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

    @Test
    void classifiesNearbyTransportAndConveniencePlacesFromSingleQuery() {
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
