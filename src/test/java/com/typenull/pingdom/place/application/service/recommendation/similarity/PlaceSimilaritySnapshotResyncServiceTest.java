package com.typenull.pingdom.place.application.service.recommendation.similarity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.recommendation.snapshot.PlaceSimilaritySnapshot;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceCoordinateQueryRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceSimilaritySnapshotRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class PlaceSimilaritySnapshotResyncServiceTest {

    @Mock
    private MapPlaceCoordinateQueryRepository mapPlaceCoordinateQueryRepository;

    @Mock
    private PlaceSimilaritySnapshotRepository placeSimilaritySnapshotRepository;

    @Mock
    private PlaceRecommendationSimilarityService placeRecommendationSimilarityService;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private PlaceSimilaritySnapshotResyncService placeSimilaritySnapshotResyncService;

    /**
     * 주변 조회가 반환한 쌍은 충돌 처리 SQL로 저장하고 이탈한 기존 쌍만 삭제하며 전체 좌표 페이지 조회를 하지 않는지 확인.
     */
    @Test
    void upsertsNearbyAndDeletesStalePairs() {
        List<MapPlace> places = createPlaces(3);
        MapPlace targetPlace = places.get(0);
        MapPlace nearbyPlace = places.get(1);
        MapPlace stalePlace = places.get(2);
        PlaceSimilaritySnapshot currentSnapshot = PlaceSimilaritySnapshot.builder()
                .id(100L)
                .leftPlaceId(targetPlace.getId())
                .rightPlaceId(nearbyPlace.getId())
                .updatedAt(LocalDateTime.now())
                .build();
        PlaceSimilaritySnapshot staleSnapshot = PlaceSimilaritySnapshot.builder()
                .id(101L)
                .leftPlaceId(targetPlace.getId())
                .rightPlaceId(stalePlace.getId())
                .updatedAt(LocalDateTime.now())
                .build();

        when(mapPlaceCoordinateQueryRepository.findNearbyPlaces(targetPlace.getId(), 20_000d))
                .thenReturn(List.of(nearbyPlace));
        when(placeRecommendationSimilarityService.score(any(MapPlace.class), any(MapPlace.class), any()))
                .thenReturn(new PlaceRecommendationSimilarityService.SimilarityScore(0.11, 0.22, 0.33, 0.44, 0.55, false));
        when(placeSimilaritySnapshotRepository.findByPlaceId(targetPlace.getId()))
                .thenReturn(List.of(currentSnapshot, staleSnapshot));

        PlaceSimilaritySnapshotResyncService.SimilaritySnapshotResyncResult result =
                placeSimilaritySnapshotResyncService.resyncPlace(targetPlace);

        assertEquals(1L, result.synchronizedSnapshotCount());
        assertEquals(1L, result.deletedSnapshotCount());
        verify(jdbcTemplate).batchUpdate(contains("ON CONFLICT"), any(BatchPreparedStatementSetter.class));
        verify(placeSimilaritySnapshotRepository).deleteAllByIdInBatch(List.of(101L));
        verify(mapPlaceCoordinateQueryRepository, never()).findCoordinatePage(any(PageRequest.class));
    }

    /**
     * 주변 장소가 없으면 기존 쌍만 삭제하고 upsert 배치를 실행하지 않는지 확인.
     */
    @Test
    void deletesPairsWhenNeighborsMissing() {
        MapPlace targetPlace = createPlaces(1).getFirst();
        PlaceSimilaritySnapshot staleSnapshot = PlaceSimilaritySnapshot.builder()
                .id(101L)
                .leftPlaceId(targetPlace.getId())
                .rightPlaceId(999L)
                .updatedAt(LocalDateTime.now())
                .build();

        when(mapPlaceCoordinateQueryRepository.findNearbyPlaces(targetPlace.getId(), 20_000d))
                .thenReturn(List.of());
        when(placeSimilaritySnapshotRepository.findByPlaceId(targetPlace.getId()))
                .thenReturn(List.of(staleSnapshot));

        PlaceSimilaritySnapshotResyncService.SimilaritySnapshotResyncResult result =
                placeSimilaritySnapshotResyncService.resyncPlace(targetPlace);

        assertEquals(0L, result.synchronizedSnapshotCount());
        assertEquals(1L, result.deletedSnapshotCount());
        verify(jdbcTemplate, never()).batchUpdate(contains("ON CONFLICT"), any(BatchPreparedStatementSetter.class));
        verify(placeSimilaritySnapshotRepository).deleteAllByIdInBatch(List.of(101L));
    }

    /**
     * 모든 쌍이 가까운 범위에 위치하도록 위도를 조금씩 늘린 장소 목록을 생성.
     */
    private List<MapPlace> createPlaces(int count) {
        List<MapPlace> places = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            long placeId = index + 1L;
            places.add(MapPlace.builder()
                    .id(placeId)
                    .name("place-" + placeId)
                    .address("address-" + placeId)
                    .latitude(35.1800d + (index * 0.0001d))
                    .longitude(128.1078d)
                    .userId(placeId)
                    .registrant("tester-" + placeId)
                    .photoCount(1L)
                    .build());
        }
        return places;
    }

}
