package com.typenull.pingdom.place.application.service.recommendation.snapshot;

import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.recommendation.engagement.PlaceRecommendationConversionType;
import com.typenull.pingdom.place.domain.recommendation.snapshot.PlaceRecommendationVersionSnapshot;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationClickRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationConversionRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationExposureRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationVersionSnapshotRepository;
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
 * 장소·추천 버전별 상호작용 집계를 증분 반영하거나 원본 이벤트에서 재생성.
 * 최초 생성은 장소 ID 순서의 잠금과 재조회로 조정하며 기존 행의 증분 갱신에는 별도 쓰기 잠금이 없음.
 * 재동기화는 실제 원본 이벤트가 있는 장소·버전만 남기고 오래된 조합과 삭제된 장소의 스냅샷을 제거.
 */
@Service
@RequiredArgsConstructor
public class PlaceRecommendationVersionSnapshotService {

    private static final int RESYNC_BATCH_SIZE = 500;

    private final PlaceRecommendationVersionSnapshotRepository placeRecommendationVersionSnapshotRepository;
    private final MapPlaceRepository mapPlaceRepository;
    private final PlaceRecommendationExposureRepository placeRecommendationExposureRepository;
    private final PlaceRecommendationClickRepository placeRecommendationClickRepository;
    private final PlaceRecommendationConversionRepository placeRecommendationConversionRepository;

    @Transactional
    public void increaseExposureCounts(List<Long> placeIds, String recommendationVersion) {
        increaseCounts(placeIds, recommendationVersion, CountType.EXPOSURE);
    }

    @Transactional
    public void increaseClickCounts(List<Long> placeIds, String recommendationVersion) {
        increaseCounts(placeIds, recommendationVersion, CountType.CLICK);
    }

    /**
     * 장소·추천 버전 스냅샷을 읽거나 생성하고 BOOKMARK이면 북마크 전환, 그 외에는 좋아요 전환 수를 1 증가시킴.
     * 기존 스냅샷 갱신은 별도 쓰기 잠금 없이 수행하므로 동시 증가의 직렬화는 보장 범위에서 제외.
     */
    @Transactional
    public void increaseConversionCount(
            Long placeId,
            String recommendationVersion,
            PlaceRecommendationConversionType conversionType
    ) {
        LocalDateTime now = LocalDateTime.now();
        PlaceRecommendationVersionSnapshot snapshot = loadOrCreateSnapshot(placeId, recommendationVersion, now);
        if (conversionType == PlaceRecommendationConversionType.BOOKMARK) {
            snapshot.increaseBookmarkConversionCount(1L, now);
        } else {
            snapshot.increaseLikeConversionCount(1L, now);
        }
        placeRecommendationVersionSnapshotRepository.save(snapshot);
    }

    /**
     * 전체 장소를 ID 순 페이지로 순회해 원본 이벤트의 장소·버전별 집계를 재작성하고 집계가 없거나 장소가 삭제된 행을 정리.
     * 장소가 하나도 없으면 모든 버전 스냅샷을 삭제하며 동기화·삭제 건수를 반환. 전체 원본 이벤트에 대한 일괄 잠금은 미사용.
     */
    @Transactional
    public VersionSnapshotResyncResult resyncAll() {
        long placeCount = mapPlaceRepository.count();
        if (placeCount == 0L) {
            long deletedSnapshotCount = placeRecommendationVersionSnapshotRepository.count();
            if (deletedSnapshotCount > 0L) {
                placeRecommendationVersionSnapshotRepository.deleteAllInBatch();
            }
            return new VersionSnapshotResyncResult(0L, deletedSnapshotCount);
        }

        LocalDateTime syncedAt = LocalDateTime.now();
        Set<Long> activePlaceIds = new HashSet<>();
        long synchronizedSnapshotCount = 0L;
        long deletedSnapshotCount = 0L;
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

            VersionSnapshotResyncResult chunkResult = synchronizeVersions(placeIds, syncedAt);
            synchronizedSnapshotCount += chunkResult.synchronizedSnapshotCount();
            deletedSnapshotCount += chunkResult.deletedSnapshotCount();

            if (!placePage.hasNext()) {
                break;
            }
            pageNumber++;
        }

        List<Long> orphanSnapshotIds = collectOrphanSnapshotIds(activePlaceIds);
        if (!orphanSnapshotIds.isEmpty()) {
            placeRecommendationVersionSnapshotRepository.deleteAllByIdInBatch(orphanSnapshotIds);
            deletedSnapshotCount += orphanSnapshotIds.size();
        }

        return new VersionSnapshotResyncResult(synchronizedSnapshotCount, deletedSnapshotCount);
    }

    @Transactional
    public VersionSnapshotResyncResult resyncPlace(Long placeId) {
        if (mapPlaceRepository.findByIdForUpdate(placeId).isEmpty()) {
            long deletedSnapshotCount = placeRecommendationVersionSnapshotRepository.deleteByPlaceId(placeId);
            return new VersionSnapshotResyncResult(0L, deletedSnapshotCount);
        }

        return synchronizeVersions(List.of(placeId), LocalDateTime.now());
    }

    private VersionSnapshotResyncResult synchronizeVersions(List<Long> placeIds, LocalDateTime syncedAt) {
        Map<SnapshotKey, Counts> countsByKey = new HashMap<>();
        accumulateExposureCounts(placeIds, countsByKey);
        accumulateClickCounts(placeIds, countsByKey);
        accumulateConversionCounts(placeIds, countsByKey);

        List<PlaceRecommendationVersionSnapshot> existingSnapshots =
                placeRecommendationVersionSnapshotRepository.findByPlaceIdIn(placeIds);
        Map<SnapshotKey, PlaceRecommendationVersionSnapshot> existingSnapshotByKey = new HashMap<>();
        for (PlaceRecommendationVersionSnapshot existingSnapshot : existingSnapshots) {
            existingSnapshotByKey.put(
                    new SnapshotKey(existingSnapshot.getPlaceId(), existingSnapshot.getRecommendationVersion()),
                    existingSnapshot
            );
        }

        List<PlaceRecommendationVersionSnapshot> snapshotsToSave = new ArrayList<>();
        for (Map.Entry<SnapshotKey, Counts> entry : countsByKey.entrySet()) {
            SnapshotKey key = entry.getKey();
            PlaceRecommendationVersionSnapshot snapshot = existingSnapshotByKey.get(key);
            if (snapshot == null) {
                snapshot = PlaceRecommendationVersionSnapshot.builder()
                        .placeId(key.placeId())
                        .recommendationVersion(key.recommendationVersion())
                        .updatedAt(syncedAt)
                        .build();
            }

            Counts counts = entry.getValue();
            snapshot.synchronize(
                    counts.clickCount(),
                    counts.bookmarkConversionCount(),
                    counts.likeConversionCount(),
                    counts.exposureCount(),
                    syncedAt
            );
            snapshotsToSave.add(snapshot);
        }

        if (!snapshotsToSave.isEmpty()) {
            placeRecommendationVersionSnapshotRepository.saveAll(snapshotsToSave);
        }

        List<Long> staleSnapshotIds = existingSnapshots.stream()
                .filter(snapshot -> !countsByKey.containsKey(
                        new SnapshotKey(snapshot.getPlaceId(), snapshot.getRecommendationVersion())
                ))
                .map(PlaceRecommendationVersionSnapshot::getId)
                .toList();
        if (!staleSnapshotIds.isEmpty()) {
            placeRecommendationVersionSnapshotRepository.deleteAllByIdInBatch(staleSnapshotIds);
        }

        return new VersionSnapshotResyncResult(snapshotsToSave.size(), staleSnapshotIds.size());
    }

    private void increaseCounts(List<Long> placeIds, String recommendationVersion, CountType countType) {
        if (placeIds.isEmpty()) {
            return;
        }

        Map<Long, Long> increments = new HashMap<>();
        for (Long placeId : placeIds) {
            increments.merge(placeId, 1L, Long::sum);
        }

        LocalDateTime now = LocalDateTime.now();
        Map<Long, PlaceRecommendationVersionSnapshot> snapshotsByPlaceId = new HashMap<>();
        for (PlaceRecommendationVersionSnapshot snapshot :
                placeRecommendationVersionSnapshotRepository.findByPlaceIdInAndRecommendationVersion(
                        increments.keySet(),
                        recommendationVersion
                )) {
            snapshotsByPlaceId.put(snapshot.getPlaceId(), snapshot);
        }

        Map<Long, PlaceRecommendationVersionSnapshot> createdSnapshots = createSnapshots(
                increments.keySet(),
                recommendationVersion,
                snapshotsByPlaceId,
                now
        );
        snapshotsByPlaceId.putAll(createdSnapshots);

        List<PlaceRecommendationVersionSnapshot> snapshotsToSave = new ArrayList<>(increments.size());
        for (Map.Entry<Long, Long> incrementEntry : increments.entrySet()) {
            PlaceRecommendationVersionSnapshot snapshot = snapshotsByPlaceId.get(incrementEntry.getKey());

            if (countType == CountType.CLICK) {
                snapshot.increaseClickCount(incrementEntry.getValue(), now);
            } else {
                snapshot.increaseExposureCount(incrementEntry.getValue(), now);
            }
            snapshotsToSave.add(snapshot);
        }

        placeRecommendationVersionSnapshotRepository.saveAll(snapshotsToSave);
    }

    private Map<Long, PlaceRecommendationVersionSnapshot> createSnapshots(
            Iterable<Long> placeIds,
            String recommendationVersion,
            Map<Long, PlaceRecommendationVersionSnapshot> existingSnapshots,
            LocalDateTime now
    ) {
        Set<Long> missingPlaceIds = new HashSet<>();
        for (Long placeId : placeIds) {
            if (!existingSnapshots.containsKey(placeId)) {
                missingPlaceIds.add(placeId);
            }
        }

        if (missingPlaceIds.isEmpty()) {
            return Map.of();
        }

        Set<Long> lockedPlaceIds = new HashSet<>();
        for (Long placeId : missingPlaceIds.stream().sorted().toList()) {
            mapPlaceRepository.findByIdForUpdate(placeId)
                    .map(MapPlace::getId)
                    .ifPresent(lockedPlaceIds::add);
        }

        Map<Long, PlaceRecommendationVersionSnapshot> snapshotsAfterLock = new HashMap<>();
        for (PlaceRecommendationVersionSnapshot snapshot :
                placeRecommendationVersionSnapshotRepository.findByPlaceIdInAndRecommendationVersionForReadLock(
                        missingPlaceIds,
                        recommendationVersion
                )) {
            snapshotsAfterLock.put(snapshot.getPlaceId(), snapshot);
        }

        Map<Long, PlaceRecommendationVersionSnapshot> snapshots = new HashMap<>();
        for (Long placeId : missingPlaceIds) {
            PlaceRecommendationVersionSnapshot existingSnapshot = snapshotsAfterLock.get(placeId);
            if (existingSnapshot != null) {
                snapshots.put(placeId, existingSnapshot);
                continue;
            }

            if (!lockedPlaceIds.contains(placeId)) {
                throw new IllegalArgumentException("Place not found: " + placeId);
            }
            snapshots.put(placeId, createSnapshot(placeId, recommendationVersion, now));
        }
        return snapshots;
    }

    private PlaceRecommendationVersionSnapshot loadOrCreateSnapshot(
            Long placeId,
            String recommendationVersion,
            LocalDateTime now
    ) {
        PlaceRecommendationVersionSnapshot existingSnapshot =
                placeRecommendationVersionSnapshotRepository.findByPlaceIdAndRecommendationVersion(
                        placeId,
                        recommendationVersion
                ).orElse(null);
        if (existingSnapshot != null) {
            return existingSnapshot;
        }

        mapPlaceRepository.findByIdForUpdate(placeId).orElseThrow();

        PlaceRecommendationVersionSnapshot snapshotAfterLock =
                placeRecommendationVersionSnapshotRepository.findByPlaceIdAndRecommendationVersionForReadLock(
                        placeId,
                        recommendationVersion
                ).orElse(null);
        if (snapshotAfterLock != null) {
            return snapshotAfterLock;
        }

        return createSnapshot(placeId, recommendationVersion, now);
    }

    private PlaceRecommendationVersionSnapshot createSnapshot(
            Long placeId,
            String recommendationVersion,
            LocalDateTime now
    ) {
        return PlaceRecommendationVersionSnapshot.builder()
                .placeId(placeId)
                .recommendationVersion(recommendationVersion)
                .clickCount(0L)
                .bookmarkConversionCount(0L)
                .likeConversionCount(0L)
                .exposureCount(0L)
                .updatedAt(now)
                .build();
    }

    private List<Long> collectOrphanSnapshotIds(Set<Long> activePlaceIds) {
        List<Long> orphanSnapshotIds = new ArrayList<>();
        int pageNumber = 0;

        while (true) {
            Page<PlaceRecommendationVersionSnapshot> snapshotPage = placeRecommendationVersionSnapshotRepository.findAll(
                    PageRequest.of(pageNumber, RESYNC_BATCH_SIZE, Sort.by(Sort.Order.asc("id")))
            );
            if (snapshotPage.isEmpty()) {
                break;
            }

            for (PlaceRecommendationVersionSnapshot snapshot : snapshotPage.getContent()) {
                if (!activePlaceIds.contains(snapshot.getPlaceId())) {
                    orphanSnapshotIds.add(snapshot.getId());
                }
            }

            if (!snapshotPage.hasNext()) {
                break;
            }
            pageNumber++;
        }

        return orphanSnapshotIds;
    }

    private void accumulateExposureCounts(List<Long> placeIds, Map<SnapshotKey, Counts> countsByKey) {
        for (PlaceRecommendationExposureRepository.PlaceVersionExposureCountProjection projection :
                placeRecommendationExposureRepository
                        .countExposuresByPlaceIdsGroupedByPlaceIdAndRecommendationVersion(placeIds)) {
            countsByKey.computeIfAbsent(
                    new SnapshotKey(projection.getPlaceId(), projection.getRecommendationVersion()),
                    ignored -> Counts.empty()
            ).addExposureCount(projection.getExposureCount());
        }
    }

    private void accumulateClickCounts(List<Long> placeIds, Map<SnapshotKey, Counts> countsByKey) {
        for (PlaceRecommendationClickRepository.PlaceVersionClickCountProjection projection :
                placeRecommendationClickRepository
                        .countClicksByPlaceIdsGroupedByPlaceIdAndRecommendationVersion(placeIds)) {
            countsByKey.computeIfAbsent(
                    new SnapshotKey(projection.getPlaceId(), projection.getRecommendationVersion()),
                    ignored -> Counts.empty()
            ).addClickCount(projection.getClickCount());
        }
    }

    private void accumulateConversionCounts(List<Long> placeIds, Map<SnapshotKey, Counts> countsByKey) {
        for (PlaceRecommendationConversionRepository.PlaceVersionConversionCountProjection projection :
                placeRecommendationConversionRepository
                        .countConversionsByPlaceIdsGroupedByPlaceIdAndRecommendationVersion(placeIds)) {
            Counts counts = countsByKey.computeIfAbsent(
                    new SnapshotKey(projection.getPlaceId(), projection.getRecommendationVersion()),
                    ignored -> Counts.empty()
            );
            if (projection.getConversionType() == PlaceRecommendationConversionType.BOOKMARK) {
                counts.addBookmarkConversionCount(projection.getConversionCount());
                continue;
            }
            counts.addLikeConversionCount(projection.getConversionCount());
        }
    }

    private enum CountType {
        CLICK,
        EXPOSURE
    }

    private record SnapshotKey(Long placeId, String recommendationVersion) {
    }

    private static class Counts {
        private long clickCount;
        private long bookmarkConversionCount;
        private long likeConversionCount;
        private long exposureCount;

        private static Counts empty() {
            return new Counts();
        }

        private void addClickCount(long delta) {
            clickCount += delta;
        }

        private void addBookmarkConversionCount(long delta) {
            bookmarkConversionCount += delta;
        }

        private void addLikeConversionCount(long delta) {
            likeConversionCount += delta;
        }

        private void addExposureCount(long delta) {
            exposureCount += delta;
        }

        private long clickCount() {
            return clickCount;
        }

        private long bookmarkConversionCount() {
            return bookmarkConversionCount;
        }

        private long likeConversionCount() {
            return likeConversionCount;
        }

        private long exposureCount() {
            return exposureCount;
        }
    }

    public record VersionSnapshotResyncResult(long synchronizedSnapshotCount, long deletedSnapshotCount) {
    }
}
