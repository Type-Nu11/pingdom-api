package com.typenull.pingdom.place.application.service.recommendation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.place.application.service.recommendation.PlaceRecommendationSimilarityService;
import com.typenull.pingdom.place.application.service.recommendation.PlaceSimilaritySnapshotResyncService;
import com.typenull.pingdom.place.domain.place.MapPlace;
import com.typenull.pingdom.place.domain.recommendation.PlaceSimilaritySnapshot;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceCoordinateQueryRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceSimilaritySnapshotRepository;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlaceSimilaritySnapshotResyncServiceTest {

    @Mock
    private MapPlaceCoordinateQueryRepository mapPlaceCoordinateQueryRepository;

    @Mock
    private PlaceSimilaritySnapshotRepository placeSimilaritySnapshotRepository;

    @Mock
    private PlaceRecommendationSimilarityService placeRecommendationSimilarityService;

    @Mock
    private EntityManager entityManager;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private PlaceSimilaritySnapshotResyncService placeSimilaritySnapshotResyncService;

    @BeforeEach
    void setUp() {
        when(placeRecommendationSimilarityService.cachedTotalBookmarkUserCount()).thenReturn(0L);
        when(placeRecommendationSimilarityService.buildContext(anyCollection(), anyMap(), anyBoolean(), any()))
                .thenReturn(new PlaceRecommendationSimilarityService.SimilarityContext(
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        0L
                ));
        when(placeRecommendationSimilarityService.score(any(MapPlace.class), any(MapPlace.class), any()))
                .thenReturn(new PlaceRecommendationSimilarityService.SimilarityScore(
                        0.11d,
                        0.22d,
                        0.33d,
                        0.44d,
                        0.55d,
                        false
                ));
    }

    @Test
    void 기존_snapshot은_saveAll_대신_update_배치로_처리한다() {
        List<MapPlace> places = createPlaces(34);
        List<PlaceSimilaritySnapshot> existingSnapshots = createExistingSnapshots(places);

        when(mapPlaceCoordinateQueryRepository.findCoordinatePage(any(PageRequest.class)))
                .thenAnswer(invocation -> coordinatePage(places, invocation.getArgument(0)));
        when(placeSimilaritySnapshotRepository.findExistingSnapshotSlice(anyLong(), any(Pageable.class)))
                .thenAnswer(invocation -> snapshotSlice(
                        existingSnapshots,
                        invocation.getArgument(0),
                        invocation.getArgument(1)
                ));
        when(placeSimilaritySnapshotRepository.count()).thenReturn((long) existingSnapshots.size());

        PlaceSimilaritySnapshotResyncService.SimilaritySnapshotResyncResult result =
                placeSimilaritySnapshotResyncService.resyncAll();

        assertEquals(existingSnapshots.size(), result.synchronizedSnapshotCount());
        assertEquals(0L, result.deletedSnapshotCount());
        verify(placeSimilaritySnapshotRepository, never()).saveAll(any());
        verify(jdbcTemplate, atLeastOnce()).batchUpdate(anyString(), any(BatchPreparedStatementSetter.class));
    }

    private Page<MapPlace> coordinatePage(List<MapPlace> places, PageRequest pageable) {
        if (pageable.getPageNumber() > 0) {
            return Page.empty(pageable);
        }
        return new PageImpl<>(places, pageable, places.size());
    }

    private Slice<PlaceSimilaritySnapshotRepository.ExistingSnapshotProjection> snapshotSlice(
            List<PlaceSimilaritySnapshot> snapshots,
            long lastSeenSnapshotId,
            Pageable pageable
    ) {
        List<PlaceSimilaritySnapshot> filteredSnapshots = snapshots.stream()
                .filter(snapshot -> snapshot.getId() > lastSeenSnapshotId)
                .toList();
        List<PlaceSimilaritySnapshotRepository.ExistingSnapshotProjection> content = filteredSnapshots.stream()
                .limit(pageable.getPageSize())
                .map(this::toProjection)
                .toList();
        boolean hasNext = filteredSnapshots.size() > content.size();
        return new SliceImpl<>(content, pageable, hasNext);
    }

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

    private List<PlaceSimilaritySnapshot> createExistingSnapshots(List<MapPlace> places) {
        List<PlaceSimilaritySnapshot> snapshots = new ArrayList<>();
        long snapshotId = 1L;
        for (int leftIndex = 0; leftIndex < places.size(); leftIndex++) {
            for (int rightIndex = leftIndex + 1; rightIndex < places.size(); rightIndex++) {
                MapPlace leftPlace = places.get(leftIndex);
                MapPlace rightPlace = places.get(rightIndex);
                snapshots.add(PlaceSimilaritySnapshot.builder()
                        .id(snapshotId++)
                        .leftPlaceId(leftPlace.getId())
                        .rightPlaceId(rightPlace.getId())
                        .geoKernelScore(0.01d)
                        .coBookmarkPmiScore(0.02d)
                        .coLikeCosineScore(0.03d)
                        .trendSimilarityScore(0.04d)
                        .totalSimilarityScore(0.05d)
                        .updatedAt(LocalDateTime.now())
                        .build());
            }
        }
        return snapshots;
    }

    private PlaceSimilaritySnapshotRepository.ExistingSnapshotProjection toProjection(PlaceSimilaritySnapshot snapshot) {
        return new PlaceSimilaritySnapshotRepository.ExistingSnapshotProjection() {
            @Override
            public Long getId() {
                return snapshot.getId();
            }

            @Override
            public Long getLeftPlaceId() {
                return snapshot.getLeftPlaceId();
            }

            @Override
            public Long getRightPlaceId() {
                return snapshot.getRightPlaceId();
            }
        };
    }
}
