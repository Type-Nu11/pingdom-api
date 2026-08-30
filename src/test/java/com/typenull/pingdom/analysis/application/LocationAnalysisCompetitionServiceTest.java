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
        MapPlaceCoordinateQueryRepository.NearbyCategoryPlace candidate = mock(
                MapPlaceCoordinateQueryRepository.NearbyCategoryPlace.class
        );
        when(candidate.getPlaceId()).thenReturn(10L);
        when(candidate.getName()).thenReturn("동일 업종 매장");
        when(candidate.getCategory()).thenReturn("CAFE");
        when(candidate.getAddress()).thenReturn("서울 강남구 테헤란로 1");
        when(candidate.getDistanceMeters()).thenReturn(42.5d);
        when(repository.findNearbyPlacesByCategory(
                eq(35.1d), eq(128.1d), eq("CAFE"), eq(100d), any(Pageable.class)
        ))
                .thenReturn(List.of(candidate));

        LocationAnalysisContent content = new LocationAnalysisContent(
                "보고서", null, null, null, null, null, null, null, null,
                List.of(new LocationAnalysisContent.RecommendedPlace(
                        1, "추천 장소", "주소", 80d, "근거", List.of(), 35.1d, 128.1d
                )), null, List.of(), List.of()
        );

        LocationAnalysisContent enriched = new LocationAnalysisCompetitionService(repository)
                .enrich(content, "카페");

        assertThat(enriched.nearbyFacilities().competitors()).singleElement()
                .extracting(LocationAnalysisContent.Facility::name)
                .isEqualTo("동일 업종 매장");
        verify(repository).findNearbyPlacesByCategory(
                eq(35.1d), eq(128.1d), eq("CAFE"), eq(100d), any(Pageable.class)
        );
    }
}
