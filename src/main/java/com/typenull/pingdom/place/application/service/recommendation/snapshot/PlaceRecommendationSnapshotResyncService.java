package com.typenull.pingdom.place.application.service.recommendation.snapshot;

import com.typenull.pingdom.place.application.service.recommendation.similarity.PlaceSimilaritySnapshotResyncService;
import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.recommendation.engagement.PlaceRecommendationConversionType;
import com.typenull.pingdom.place.domain.recommendation.snapshot.PlaceRecommendationSnapshot;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationClickRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapBookmarkRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationConversionRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationExposureRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationSnapshotRepository;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 원본 북마크·게시물·추천 이벤트를 집계하여 전체 장소 스냅샷을 보정하고 버전·유사도 재동기화를 조정합니다.
 * 전체 실행은 500개씩 조회하지만 모든 묶음과 후속 재동기화가 같은 트랜잭션에 참여합니다.
 * 단일 장소 보정은 해당 장소를 잠그며 장소가 없으면 관련 스냅샷을 제거합니다.
 */
@Service
@RequiredArgsConstructor
public class PlaceRecommendationSnapshotResyncService {

    private static final int RESYNC_BATCH_SIZE = 500;

    private final MapPlaceRepository mapPlaceRepository;
    private final MapBookmarkRepository mapBookmarkRepository;
    private final MapImageRepository mapImageRepository;
    private final PlaceRecommendationClickRepository placeRecommendationClickRepository;
    private final PlaceRecommendationConversionRepository placeRecommendationConversionRepository;
    private final PlaceRecommendationExposureRepository placeRecommendationExposureRepository;
    private final PlaceRecommendationSnapshotRepository placeRecommendationSnapshotRepository;
    private final PlaceSimilaritySnapshotResyncService placeSimilaritySnapshotResyncService;
    private final PlaceRecommendationVersionSnapshotService placeRecommendationVersionSnapshotService;

    /**
     * 전체 장소를 ID 순 페이지로 순회하며 원본 집계로 추천 스냅샷을 덮어쓰고 삭제된 장소의 스냅샷을 정리합니다.
     * 유사도·버전별 스냅샷 재동기화도 같은 호출 트랜잭션에 연결하고 종류별 갱신·삭제 건수를 반환합니다.
     * 전체 장소·원본 이벤트에 일괄 잠금을 걸지 않으므로 동시 쓰기와 격리된 시점의 전체 재구성을 보장하지는 않습니다.
     */
    @Transactional
    public SnapshotResyncResult resyncAll() {
        long placeCount = mapPlaceRepository.count();
        if (placeCount == 0L) {
            long deletedSnapshotCount = placeRecommendationSnapshotRepository.count();
            if (deletedSnapshotCount > 0L) {
                placeRecommendationSnapshotRepository.deleteAllInBatch();
            }
            PlaceRecommendationVersionSnapshotService.VersionSnapshotResyncResult versionResult =
                    placeRecommendationVersionSnapshotService.resyncAll();
            PlaceSimilaritySnapshotResyncService.SimilaritySnapshotResyncResult similarityResult =
                    placeSimilaritySnapshotResyncService.resyncAll();
            return new SnapshotResyncResult(
                    0,
                    0,
                    deletedSnapshotCount,
                    similarityResult.synchronizedSnapshotCount(),
                    similarityResult.deletedSnapshotCount(),
                    versionResult.synchronizedSnapshotCount(),
                    versionResult.deletedSnapshotCount()
            );
        }

        LocalDateTime syncedAt = LocalDateTime.now();
        Set<Long> activePlaceIds = new HashSet<>();
        long synchronizedSnapshotCount = 0L;
        int pageNumber = 0;

        while (true) {
            Page<MapPlace> placePage = mapPlaceRepository.findAll(
                    PageRequest.of(pageNumber, RESYNC_BATCH_SIZE, Sort.by(Sort.Order.asc("id")))
            );
            if (placePage.isEmpty()) {
                break;
            }

            List<MapPlace> places = placePage.getContent();
            List<Long> placeIds = places.stream()
                    .map(MapPlace::getId)
                    .toList();
            activePlaceIds.addAll(placeIds);
            synchronizedSnapshotCount += synchronizeSnapshots(places, syncedAt);

            if (!placePage.hasNext()) {
                break;
            }
            pageNumber++;
        }

        List<Long> orphanSnapshotPlaceIds = collectOrphanSnapshotPlaceIds(activePlaceIds);

        if (!orphanSnapshotPlaceIds.isEmpty()) {
            placeRecommendationSnapshotRepository.deleteAllByIdInBatch(orphanSnapshotPlaceIds);
        }

        PlaceSimilaritySnapshotResyncService.SimilaritySnapshotResyncResult similarityResult =
                placeSimilaritySnapshotResyncService.resyncAll();
        PlaceRecommendationVersionSnapshotService.VersionSnapshotResyncResult versionResult =
                placeRecommendationVersionSnapshotService.resyncAll();

        return new SnapshotResyncResult(
                placeCount,
                synchronizedSnapshotCount,
                orphanSnapshotPlaceIds.size(),
                similarityResult.synchronizedSnapshotCount(),
                similarityResult.deletedSnapshotCount(),
                versionResult.synchronizedSnapshotCount(),
                versionResult.deletedSnapshotCount()
        );
    }

    /**
     * 장소 행을 쓰기 잠금으로 읽어 해당 장소 집계와 주변 유사도·버전별 집계를 재구성하고 처리 건수를 반환합니다.
     * 장소가 이미 없으면 오류 대신 해당 ID의 남은 스냅샷들을 정리합니다. 원본 반응 행 전체를 잠그는 작업은 아닙니다.
     */
    @Transactional
    public SnapshotResyncResult resyncPlace(Long placeId) {
        MapPlace place = mapPlaceRepository.findByIdForUpdate(placeId).orElse(null);
        if (place == null) {
            boolean snapshotExists = placeRecommendationSnapshotRepository.existsById(placeId);
            if (snapshotExists) {
                placeRecommendationSnapshotRepository.deleteById(placeId);
            }
            long deletedSimilaritySnapshotCount = placeSimilaritySnapshotResyncService.deleteForPlace(placeId);
            PlaceRecommendationVersionSnapshotService.VersionSnapshotResyncResult versionResult =
                    placeRecommendationVersionSnapshotService.resyncPlace(placeId);
            return new SnapshotResyncResult(
                    0L,
                    0L,
                    snapshotExists ? 1L : 0L,
                    0L,
                    deletedSimilaritySnapshotCount,
                    versionResult.synchronizedSnapshotCount(),
                    versionResult.deletedSnapshotCount()
            );
        }

        LocalDateTime syncedAt = LocalDateTime.now();
        synchronizeSnapshots(List.of(place), syncedAt);

        PlaceSimilaritySnapshotResyncService.SimilaritySnapshotResyncResult similarityResult =
                placeSimilaritySnapshotResyncService.resyncPlace(place);
        PlaceRecommendationVersionSnapshotService.VersionSnapshotResyncResult versionResult =
                placeRecommendationVersionSnapshotService.resyncPlace(placeId);

        return new SnapshotResyncResult(
                1L,
                1L,
                0L,
                similarityResult.synchronizedSnapshotCount(),
                similarityResult.deletedSnapshotCount(),
                versionResult.synchronizedSnapshotCount(),
                versionResult.deletedSnapshotCount()
        );
    }

    /**
     * 병합 대상의 전체 집계를 원본에서 다시 만들고 원본 장소의 전체 스냅샷만 제거합니다.
     * 이 메서드는 버전·유사도 스냅샷 재동기화를 호출하지 않으므로 병합 호출 흐름에서 별도로 조정해야 합니다.
     */
    @Transactional
    public void resyncMergedPlace(Long sourcePlaceId, Long targetPlaceId) {
        LocalDateTime syncedAt = LocalDateTime.now();
        MapPlace targetPlace = mapPlaceRepository.findById(targetPlaceId)
                .orElseThrow(() -> new MapException(MapErrorCode.PLACE_NOT_FOUND));
        List<Long> targetPlaceIds = List.of(targetPlaceId);

        Map<Long, Long> bookmarkCounts = loadBookmarkCounts(targetPlaceIds);
        Map<Long, ImageAggregate> imageAggregates = loadImageAggregates(targetPlaceIds);
        Map<Long, Long> clickCounts = loadClickCounts(targetPlaceIds);
        Map<Long, ConversionCounts> conversionCounts = loadConversionCounts(targetPlaceIds);
        Map<Long, Long> exposureCounts = loadExposureCounts(targetPlaceIds);
        PlaceRecommendationSnapshot snapshot = placeRecommendationSnapshotRepository.findById(targetPlaceId)
                .orElseGet(() -> PlaceRecommendationSnapshot.builder()
                        .placeId(targetPlaceId)
                        .updatedAt(syncedAt)
                        .build());
        ImageAggregate imageAggregate = imageAggregates.getOrDefault(targetPlaceId, ImageAggregate.empty());

        snapshot.synchronize(
                targetPlace.currentPhotoCount(),
                bookmarkCounts.getOrDefault(targetPlaceId, 0L),
                imageAggregate.totalLikeCount(),
                clickCounts.getOrDefault(targetPlaceId, 0L),
                conversionCounts.getOrDefault(targetPlaceId, ConversionCounts.empty()).bookmarkConversionCount(),
                conversionCounts.getOrDefault(targetPlaceId, ConversionCounts.empty()).likeConversionCount(),
                exposureCounts.getOrDefault(targetPlaceId, 0L),
                imageAggregate.latestPostCreatedAt(),
                syncedAt
        );

        placeRecommendationSnapshotRepository.save(snapshot);
        placeRecommendationSnapshotRepository.deleteById(sourcePlaceId);
    }

    private long synchronizeSnapshots(List<MapPlace> places, LocalDateTime syncedAt) {
        List<Long> placeIds = places.stream()
                .map(MapPlace::getId)
                .toList();
        Map<Long, Long> bookmarkCounts = loadBookmarkCounts(placeIds);
        Map<Long, ImageAggregate> imageAggregates = loadImageAggregates(placeIds);
        Map<Long, Long> clickCounts = loadClickCounts(placeIds);
        Map<Long, ConversionCounts> conversionCounts = loadConversionCounts(placeIds);
        Map<Long, Long> exposureCounts = loadExposureCounts(placeIds);
        Map<Long, PlaceRecommendationSnapshot> existingSnapshotsByPlaceId = new HashMap<>();
        for (PlaceRecommendationSnapshot existingSnapshot :
                placeRecommendationSnapshotRepository.findByPlaceIdIn(placeIds)) {
            existingSnapshotsByPlaceId.put(existingSnapshot.getPlaceId(), existingSnapshot);
        }

        List<PlaceRecommendationSnapshot> snapshotsToSave = new ArrayList<>(places.size());
        for (MapPlace place : places) {
            Long placeId = place.getId();
            ImageAggregate imageAggregate = imageAggregates.getOrDefault(placeId, ImageAggregate.empty());
            ConversionCounts conversionCount = conversionCounts.getOrDefault(placeId, ConversionCounts.empty());
            PlaceRecommendationSnapshot snapshot = existingSnapshotsByPlaceId.get(placeId);
            if (snapshot == null) {
                snapshot = PlaceRecommendationSnapshot.builder()
                        .placeId(placeId)
                        .updatedAt(syncedAt)
                        .build();
            }

            snapshot.synchronize(
                    place.currentPhotoCount(),
                    bookmarkCounts.getOrDefault(placeId, 0L),
                    imageAggregate.totalLikeCount(),
                    clickCounts.getOrDefault(placeId, 0L),
                    conversionCount.bookmarkConversionCount(),
                    conversionCount.likeConversionCount(),
                    exposureCounts.getOrDefault(placeId, 0L),
                    imageAggregate.latestPostCreatedAt(),
                    syncedAt
            );
            snapshotsToSave.add(snapshot);
        }

        placeRecommendationSnapshotRepository.saveAll(snapshotsToSave);
        return snapshotsToSave.size();
    }

    private List<Long> collectOrphanSnapshotPlaceIds(Set<Long> activePlaceIds) {
        List<Long> orphanSnapshotPlaceIds = new ArrayList<>();
        int pageNumber = 0;

        while (true) {
            Page<PlaceRecommendationSnapshot> snapshotPage = placeRecommendationSnapshotRepository.findAll(
                    PageRequest.of(pageNumber, RESYNC_BATCH_SIZE, Sort.by(Sort.Order.asc("placeId")))
            );
            if (snapshotPage.isEmpty()) {
                break;
            }

            for (PlaceRecommendationSnapshot snapshot : snapshotPage.getContent()) {
                if (!activePlaceIds.contains(snapshot.getPlaceId())) {
                    orphanSnapshotPlaceIds.add(snapshot.getPlaceId());
                }
            }

            if (!snapshotPage.hasNext()) {
                break;
            }
            pageNumber++;
        }

        return orphanSnapshotPlaceIds;
    }

    private Map<Long, Long> loadBookmarkCounts(List<Long> placeIds) {
        Map<Long, Long> bookmarkCounts = new HashMap<>();
        for (MapBookmarkRepository.PlaceBookmarkCountProjection projection :
                mapBookmarkRepository.findBookmarkCountsByPlaceIds(placeIds)) {
            bookmarkCounts.put(projection.getPlaceId(), projection.getBookmarkCount());
        }
        return bookmarkCounts;
    }

    private Map<Long, ImageAggregate> loadImageAggregates(List<Long> placeIds) {
        Map<Long, ImageAggregate> imageAggregates = new HashMap<>();
        for (MapImageRepository.PlaceImageAggregateProjection projection :
                mapImageRepository.findPlaceAggregatesByPlaceIds(placeIds)) {
            imageAggregates.put(
                    projection.getPlaceId(),
                    new ImageAggregate(
                            projection.getLikeSum() == null ? 0L : projection.getLikeSum(),
                            projection.getLatestCreatedAt()
                    )
            );
        }
        return imageAggregates;
    }

    private Map<Long, Long> loadExposureCounts(List<Long> placeIds) {
        Map<Long, Long> exposureCounts = new HashMap<>();
        for (PlaceRecommendationExposureRepository.PlaceExposureCountProjection projection :
                placeRecommendationExposureRepository.countExposuresByPlaceIds(placeIds)) {
            exposureCounts.put(projection.getPlaceId(), projection.getExposureCount());
        }
        return exposureCounts;
    }

    private Map<Long, Long> loadClickCounts(List<Long> placeIds) {
        Map<Long, Long> clickCounts = new HashMap<>();
        for (PlaceRecommendationClickRepository.PlaceClickCountProjection projection :
                placeRecommendationClickRepository.countClicksByPlaceIds(placeIds)) {
            clickCounts.put(projection.getPlaceId(), projection.getClickCount());
        }
        return clickCounts;
    }

    private Map<Long, ConversionCounts> loadConversionCounts(List<Long> placeIds) {
        Map<Long, ConversionCounts> conversionCounts = new HashMap<>();
        for (PlaceRecommendationConversionRepository.PlaceConversionCountProjection projection :
                placeRecommendationConversionRepository.countConversionsByPlaceIds(placeIds)) {
            ConversionCounts existing = conversionCounts.getOrDefault(projection.getPlaceId(), ConversionCounts.empty());
            if (projection.getConversionType() == PlaceRecommendationConversionType.BOOKMARK) {
                conversionCounts.put(
                        projection.getPlaceId(),
                        existing.withBookmarkConversionCount(projection.getConversionCount())
                );
                continue;
            }
            conversionCounts.put(
                    projection.getPlaceId(),
                    existing.withLikeConversionCount(projection.getConversionCount())
            );
        }
        return conversionCounts;
    }

    private record ImageAggregate(long totalLikeCount, LocalDateTime latestPostCreatedAt) {
        private static ImageAggregate empty() {
            return new ImageAggregate(0L, null);
        }
    }

    private record ConversionCounts(long bookmarkConversionCount, long likeConversionCount) {
        private static ConversionCounts empty() {
            return new ConversionCounts(0L, 0L);
        }

        private ConversionCounts withBookmarkConversionCount(long count) {
            return new ConversionCounts(count, likeConversionCount);
        }

        private ConversionCounts withLikeConversionCount(long count) {
            return new ConversionCounts(bookmarkConversionCount, count);
        }
    }

    public record SnapshotResyncResult(
            long placeCount,
            long synchronizedSnapshotCount,
            long deletedSnapshotCount,
            long synchronizedSimilaritySnapshotCount,
            long deletedSimilaritySnapshotCount,
            long synchronizedVersionSnapshotCount,
            long deletedVersionSnapshotCount
    ) {
    }
}
