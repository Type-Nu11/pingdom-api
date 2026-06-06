package com.typenull.pingdom.place.application.service;

import com.typenull.pingdom.place.domain.MapPlace;
import com.typenull.pingdom.place.domain.PlaceRecommendationConversionType;
import com.typenull.pingdom.place.domain.PlaceRecommendationVersionSnapshot;
import com.typenull.pingdom.place.infrastructure.persistence.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.PlaceRecommendationClickRepository;
import com.typenull.pingdom.place.infrastructure.persistence.PlaceRecommendationConversionRepository;
import com.typenull.pingdom.place.infrastructure.persistence.PlaceRecommendationExposureRepository;
import com.typenull.pingdom.place.infrastructure.persistence.PlaceRecommendationVersionSnapshotRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlaceRecommendationVersionSnapshotService {

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
    public VersionSnapshotResyncResult resyncAll() {
        List<MapPlace> places = mapPlaceRepository.findAll();
        Set<Long> existingPlaceIds = new HashSet<>();
        for (MapPlace place : places) {
            existingPlaceIds.add(place.getId());
        }

        List<PlaceRecommendationVersionSnapshot> existingSnapshots =
                placeRecommendationVersionSnapshotRepository.findAll();
        if (existingPlaceIds.isEmpty()) {
            long deletedSnapshotCount = existingSnapshots.size();
            if (deletedSnapshotCount > 0L) {
                placeRecommendationVersionSnapshotRepository.deleteAllInBatch();
            }
            return new VersionSnapshotResyncResult(0L, deletedSnapshotCount);
        }

        Map<SnapshotKey, Counts> countsByKey = new HashMap<>();
        accumulateExposureCounts(countsByKey);
        accumulateClickCounts(countsByKey);
        accumulateConversionCounts(countsByKey);

        LocalDateTime syncedAt = LocalDateTime.now();
        Map<SnapshotKey, PlaceRecommendationVersionSnapshot> existingSnapshotByKey = new HashMap<>();
        for (PlaceRecommendationVersionSnapshot existingSnapshot : existingSnapshots) {
            existingSnapshotByKey.put(
                    new SnapshotKey(existingSnapshot.getPlaceId(), existingSnapshot.getRecommendationVersion()),
                    existingSnapshot
            );
        }

        List<PlaceRecommendationVersionSnapshot> snapshotsToSave = new ArrayList<>();
        Set<Long> savedPlaceIds = new HashSet<>();
        for (Map.Entry<SnapshotKey, Counts> entry : countsByKey.entrySet()) {
            SnapshotKey key = entry.getKey();
            if (!existingPlaceIds.contains(key.placeId())) {
                continue;
            }
            savedPlaceIds.add(key.placeId());
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

        List<Long> orphanSnapshotIds = existingSnapshots.stream()
                .filter(snapshot -> !existingPlaceIds.contains(snapshot.getPlaceId()) || !countsByKey.containsKey(
                        new SnapshotKey(snapshot.getPlaceId(), snapshot.getRecommendationVersion())
                ))
                .map(PlaceRecommendationVersionSnapshot::getId)
                .toList();

        if (!orphanSnapshotIds.isEmpty()) {
            placeRecommendationVersionSnapshotRepository.deleteAllByIdInBatch(orphanSnapshotIds);
        }

        return new VersionSnapshotResyncResult(snapshotsToSave.size(), orphanSnapshotIds.size());
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

        Map<Long, MapPlace> placesById = new HashMap<>();
        for (MapPlace mapPlace : mapPlaceRepository.findAllById(missingPlaceIds)) {
            placesById.put(mapPlace.getId(), mapPlace);
        }

        Map<Long, PlaceRecommendationVersionSnapshot> snapshots = new HashMap<>();
        for (Long placeId : missingPlaceIds) {
            if (!placesById.containsKey(placeId)) {
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

        mapPlaceRepository.findById(placeId).orElseThrow();

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

    private void accumulateExposureCounts(Map<SnapshotKey, Counts> countsByKey) {
        for (PlaceRecommendationExposureRepository.PlaceVersionExposureCountProjection projection :
                placeRecommendationExposureRepository.countExposuresGroupedByPlaceIdAndRecommendationVersion()) {
            countsByKey.computeIfAbsent(
                    new SnapshotKey(projection.getPlaceId(), projection.getRecommendationVersion()),
                    ignored -> Counts.empty()
            ).addExposureCount(projection.getExposureCount());
        }
    }

    private void accumulateClickCounts(Map<SnapshotKey, Counts> countsByKey) {
        for (PlaceRecommendationClickRepository.PlaceVersionClickCountProjection projection :
                placeRecommendationClickRepository.countClicksGroupedByPlaceIdAndRecommendationVersion()) {
            countsByKey.computeIfAbsent(
                    new SnapshotKey(projection.getPlaceId(), projection.getRecommendationVersion()),
                    ignored -> Counts.empty()
            ).addClickCount(projection.getClickCount());
        }
    }

    private void accumulateConversionCounts(Map<SnapshotKey, Counts> countsByKey) {
        for (PlaceRecommendationConversionRepository.PlaceVersionConversionCountProjection projection :
                placeRecommendationConversionRepository.countConversionsGroupedByPlaceIdAndRecommendationVersion()) {
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
