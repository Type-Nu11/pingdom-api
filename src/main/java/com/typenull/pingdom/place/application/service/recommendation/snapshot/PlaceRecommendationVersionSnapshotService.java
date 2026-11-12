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
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 장소·추천 버전별 상호작용 집계를 증분 반영하거나 원본 이벤트에서 재생성.
 * 증분·재집계 모두 장소 ID 순서의 쓰기 잠금 후 snapshot과 원천을 다시 조회.
 * 재동기화는 실제 원본 이벤트가 있는 장소·버전만 남기고 오래된 조합과 삭제된 장소의 스냅샷을 제거.
 */
@Service
@RequiredArgsConstructor
public class PlaceRecommendationVersionSnapshotService {

    private final PlaceRecommendationVersionSnapshotRepository placeRecommendationVersionSnapshotRepository;
    private final MapPlaceRepository mapPlaceRepository;
    private final PlaceRecommendationExposureRepository placeRecommendationExposureRepository;
    private final PlaceRecommendationClickRepository placeRecommendationClickRepository;
    private final PlaceRecommendationConversionRepository placeRecommendationConversionRepository;
    private final PlaceRecommendationSnapshotWriteLock snapshotWriteLock;

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
     * 전체 snapshot과 같은 장소 잠금에 참여하여 원천 기록과 함께 확정.
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

    @Transactional
    public VersionSnapshotResyncResult resyncPlace(Long placeId) {
        if (snapshotWriteLock.lockPlace(placeId).isEmpty()) {
            long deletedSnapshotCount = placeRecommendationVersionSnapshotRepository.deleteOrphansByPlaceId(placeId);
            return new VersionSnapshotResyncResult(0L, deletedSnapshotCount);
        }

        return synchronizeVersions(List.of(placeId), LocalDateTime.now());
    }

    VersionSnapshotResyncResult synchronizeVersions(List<Long> placeIds, LocalDateTime syncedAt) {
        placeIds = snapshotWriteLock.lockPlaces(placeIds).stream().map(MapPlace::getId).toList();
        List<Long> lockedPlaceIds = placeIds;
        Map<SnapshotKey, Counts> countsByKey = new HashMap<>();
        accumulateExposureCounts(placeIds, countsByKey);
        accumulateClickCounts(placeIds, countsByKey);
        accumulateConversionCounts(placeIds, countsByKey);

        List<PlaceRecommendationVersionSnapshot> existingSnapshots =
                snapshotWriteLock.reloadSnapshots(
                        () -> placeRecommendationVersionSnapshotRepository.findByPlaceIdIn(lockedPlaceIds));
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
            snapshotWriteLock.flushAndDetach(existingSnapshots.stream()
                    .filter(snapshot -> staleSnapshotIds.contains(snapshot.getId())).toList());
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

        snapshotWriteLock.requirePlaces(increments.keySet());
        LocalDateTime now = LocalDateTime.now();
        Map<Long, PlaceRecommendationVersionSnapshot> snapshotsByPlaceId = new HashMap<>();
        for (PlaceRecommendationVersionSnapshot snapshot : snapshotWriteLock.reloadSnapshots(
                () -> placeRecommendationVersionSnapshotRepository.findByPlaceIdInAndRecommendationVersion(
                        increments.keySet(), recommendationVersion))) {
            snapshotsByPlaceId.put(snapshot.getPlaceId(), snapshot);
        }
        List<PlaceRecommendationVersionSnapshot> snapshotsToSave = new ArrayList<>(increments.size());
        for (Long placeId : increments.keySet().stream().sorted().toList()) {
            PlaceRecommendationVersionSnapshot snapshot = snapshotsByPlaceId.get(placeId);
            if (snapshot == null) {
                snapshot = createSnapshot(placeId, recommendationVersion, now);
            }
            if (countType == CountType.CLICK) {
                snapshot.increaseClickCount(increments.get(placeId), now);
            } else {
                snapshot.increaseExposureCount(increments.get(placeId), now);
            }
            snapshotsToSave.add(snapshot);
        }
        placeRecommendationVersionSnapshotRepository.saveAll(snapshotsToSave);
    }

    private PlaceRecommendationVersionSnapshot loadOrCreateSnapshot(
            Long placeId, String recommendationVersion, LocalDateTime now
    ) {
        snapshotWriteLock.requirePlace(placeId);
        return snapshotWriteLock.reloadSnapshot(
                () -> placeRecommendationVersionSnapshotRepository.findByPlaceIdAndRecommendationVersion(
                        placeId, recommendationVersion))
                .orElseGet(() -> createSnapshot(placeId, recommendationVersion, now));
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
