package com.typenull.pingdom.place.application.service.recommendation.similarity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.recommendation.snapshot.PlaceSimilaritySnapshot;
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

    /**
     * 집계 컨텍스트와 유사도 점수를 고정하여 재동기화의 저장 방식·범위·배치 크기를 분리해 검증한다.
     */
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

    /**
     * 기존 유사도 쌍을 모두 재동기화할 때 삭제 없이 JDBC 배치 갱신을 호출하고 JPA saveAll을 사용하지 않는지 확인한다.
     */
    @Test
    void updatesExistingSnapshotsInBatches() {
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

    /**
     * 33개 장소의 528개 쌍을 500건 이하의 두 JDBC 배치로 나누고 전체 갱신 건수를 유지하는지 확인한다.
     */
    @Test
    void splitsUpdatesAtBatchLimit() {
        // 33개 장소는 33 * 32 / 2 = 528개의 스냅샷 쌍을 만들어 500건 배치 분할을 검증하기에 충분하다.
        List<MapPlace> places = createPlaces(33);
        List<PlaceSimilaritySnapshot> existingSnapshots = createExistingSnapshots(places);
        List<Integer> batchSizes = new ArrayList<>();

        when(mapPlaceCoordinateQueryRepository.findCoordinatePage(any(PageRequest.class)))
                .thenAnswer(invocation -> coordinatePage(places, invocation.getArgument(0)));
        when(placeSimilaritySnapshotRepository.findExistingSnapshotSlice(anyLong(), any(Pageable.class)))
                .thenAnswer(invocation -> snapshotSlice(
                        existingSnapshots,
                        invocation.getArgument(0),
                        invocation.getArgument(1)
                ));
        doAnswer(invocation -> {
            BatchPreparedStatementSetter setter = invocation.getArgument(1);
            batchSizes.add(setter.getBatchSize());
            return new int[setter.getBatchSize()];
        }).when(jdbcTemplate).batchUpdate(anyString(), any(BatchPreparedStatementSetter.class));

        placeSimilaritySnapshotResyncService.resyncAll();

        verify(jdbcTemplate, times(2)).batchUpdate(anyString(), any(BatchPreparedStatementSetter.class));

        int totalBatchSize = batchSizes.stream()
                .mapToInt(Integer::intValue)
                .sum();

        assertTrue(batchSizes.stream().allMatch(batchSize -> batchSize <= 500));
        assertEquals(existingSnapshots.size(), totalBatchSize);
        verify(placeSimilaritySnapshotRepository, never()).saveAll(any());
    }

    /**
     * 528개 기존 쌍을 두 번의 커서 슬라이스 조회로 읽고 유사도 컨텍스트를 한 번만 만드는지 확인한다.
     */
    @Test
    void loadsSnapshotsWithCursorSlices() {
        List<MapPlace> places = createPlaces(33);
        List<PlaceSimilaritySnapshot> existingSnapshots = createExistingSnapshots(places);

        when(mapPlaceCoordinateQueryRepository.findCoordinatePage(any(PageRequest.class)))
                .thenAnswer(invocation -> coordinatePage(places, invocation.getArgument(0)));
        when(placeSimilaritySnapshotRepository.findExistingSnapshotSlice(anyLong(), any(Pageable.class)))
                .thenAnswer(invocation -> snapshotSlice(
                        existingSnapshots,
                        invocation.getArgument(0),
                        invocation.getArgument(1)
                ));

        placeSimilaritySnapshotResyncService.resyncAll();

        verify(placeSimilaritySnapshotRepository, times(2))
                .findExistingSnapshotSlice(anyLong(), any(Pageable.class));
        verify(placeRecommendationSimilarityService, times(1))
                .buildContext(anyCollection(), anyMap(), anyBoolean(), anyLong());
    }

    /**
     * 주변 조회가 반환한 쌍은 충돌 처리 SQL로 저장하고 이탈한 기존 쌍만 삭제하며 전체 좌표 페이지 조회를 하지 않는지 확인한다.
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
     * 주변 장소가 없으면 기존 쌍만 삭제하고 upsert 배치를 실행하지 않는지 확인한다.
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
     * 첫 페이지에 모든 장소를 반환하고 이후 페이지를 비워 좌표 페이지 순회를 모의한다.
     */
    private Page<MapPlace> coordinatePage(List<MapPlace> places, PageRequest pageable) {
        if (pageable.getPageNumber() > 0) {
            return Page.empty(pageable);
        }
        return new PageImpl<>(places, pageable, places.size());
    }

    /**
     * 마지막 조회 ID보다 큰 쌍을 페이지 크기만큼 반환하고 남은 데이터로 hasNext를 계산한다.
     */
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

    /**
     * 모든 쌍이 가까운 범위에 위치하도록 위도를 조금씩 늘린 장소 목록을 만든다.
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

    /**
     * 장소의 중복 없는 모든 ID 쌍에 순차 스냅샷 ID를 부여해 배치 경계 입력을 만든다.
     */
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

    /**
     * 기존 스냅샷에서 식별자와 양쪽 장소 ID만 노출하는 저장소 조회 투영을 만든다.
     */
    private PlaceSimilaritySnapshotRepository.ExistingSnapshotProjection toProjection(PlaceSimilaritySnapshot snapshot) {
        return new PlaceSimilaritySnapshotRepository.ExistingSnapshotProjection() {
            /**
             * 슬라이스 커서 비교에 사용할 기존 스냅샷 ID를 반환한다.
             */
            @Override
            public Long getId() {
                return snapshot.getId();
            }

            /**
             * 기존 유사도 쌍의 작은 쪽 장소 ID를 조회 투영에 제공한다.
             */
            @Override
            public Long getLeftPlaceId() {
                return snapshot.getLeftPlaceId();
            }

            /**
             * 기존 유사도 쌍의 큰 쪽 장소 ID를 조회 투영에 제공한다.
             */
            @Override
            public Long getRightPlaceId() {
                return snapshot.getRightPlaceId();
            }
        };
    }
}
