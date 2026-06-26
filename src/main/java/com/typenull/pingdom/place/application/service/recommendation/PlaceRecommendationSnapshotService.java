package com.typenull.pingdom.place.application.service.recommendation;

import com.typenull.pingdom.place.domain.place.MapPlace;
import com.typenull.pingdom.place.domain.recommendation.PlaceRecommendationConversionType;
import com.typenull.pingdom.place.domain.recommendation.PlaceRecommendationSnapshot;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapBookmarkRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationSnapshotRepository;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlaceRecommendationSnapshotService {

    private final PlaceRecommendationSnapshotRepository placeRecommendationSnapshotRepository;
    private final MapPlaceRepository mapPlaceRepository;
    private final MapBookmarkRepository mapBookmarkRepository;
    private final MapImageRepository mapImageRepository;

    @Transactional
    public void initialize(Long placeId) {
        refresh(placeId);
    }

    @Transactional
    public void refresh(Long placeId) {
        LocalDateTime now = LocalDateTime.now();
        PlaceRecommendationSnapshot snapshot = loadOrCreateSnapshot(placeId, now);
        MapPlace mapPlace = mapPlaceRepository.findById(placeId)
                .orElseThrow(() -> new MapException(MapErrorCode.PLACE_NOT_FOUND));

        snapshot.synchronize(
                mapPlace.currentPhotoCount(),
                mapBookmarkRepository.countByPlaceId(placeId),
                mapImageRepository.sumLikeCountByPlaceId(placeId),
                snapshot.getClickCount(),
                snapshot.getBookmarkConversionCount(),
                snapshot.getLikeConversionCount(),
                snapshot.getExposureCount(),
                mapImageRepository.findLatestCreatedAtByPlaceId(placeId),
                now
        );
        placeRecommendationSnapshotRepository.save(snapshot);
    }

    @Transactional
    public void delete(Long placeId) {
        placeRecommendationSnapshotRepository.deleteById(placeId);
    }

    @Transactional
    public void increaseExposureCounts(List<Long> placeIds) {
        increaseCounts(placeIds, CountType.EXPOSURE);
    }

    @Transactional
    public void increaseClickCounts(List<Long> placeIds) {
        increaseCounts(placeIds, CountType.CLICK);
    }

    @Transactional
    public void increaseConversionCount(Long placeId, PlaceRecommendationConversionType conversionType) {
        LocalDateTime now = LocalDateTime.now();
        PlaceRecommendationSnapshot snapshot = loadOrCreateSnapshot(placeId, now);
        if (conversionType == PlaceRecommendationConversionType.BOOKMARK) {
            snapshot.increaseBookmarkConversionCount(1L, now);
        } else {
            snapshot.increaseLikeConversionCount(1L, now);
        }
        placeRecommendationSnapshotRepository.save(snapshot);
    }

    private void increaseCounts(List<Long> placeIds, CountType countType) {
        if (placeIds.isEmpty()) {
            return;
        }

        Map<Long, Long> increments = new HashMap<>();
        for (Long placeId : placeIds) {
            increments.merge(placeId, 1L, Long::sum);
        }

        LocalDateTime now = LocalDateTime.now();
        Map<Long, PlaceRecommendationSnapshot> existingSnapshots = new HashMap<>();
        for (PlaceRecommendationSnapshot snapshot :
                placeRecommendationSnapshotRepository.findByPlaceIdIn(increments.keySet())) {
            existingSnapshots.put(snapshot.getPlaceId(), snapshot);
        }

        Map<Long, PlaceRecommendationSnapshot> createdSnapshots =
                createMissingSnapshots(increments.keySet(), existingSnapshots, now);
        existingSnapshots.putAll(createdSnapshots);

        List<PlaceRecommendationSnapshot> snapshotsToSave = new ArrayList<>(increments.size());
        for (Map.Entry<Long, Long> incrementEntry : increments.entrySet()) {
            PlaceRecommendationSnapshot snapshot = existingSnapshots.get(incrementEntry.getKey());

            if (countType == CountType.CLICK) {
                snapshot.increaseClickCount(incrementEntry.getValue(), now);
            } else {
                snapshot.increaseExposureCount(incrementEntry.getValue(), now);
            }
            snapshotsToSave.add(snapshot);
        }

        placeRecommendationSnapshotRepository.saveAll(snapshotsToSave);
    }

    private Map<Long, PlaceRecommendationSnapshot> createMissingSnapshots(
            Iterable<Long> placeIds,
            Map<Long, PlaceRecommendationSnapshot> existingSnapshots,
            LocalDateTime now
    ) {
        List<Long> missingPlaceIds = new ArrayList<>();
        for (Long placeId : placeIds) {
            if (!existingSnapshots.containsKey(placeId)) {
                missingPlaceIds.add(placeId);
            }
        }

        if (missingPlaceIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, MapPlace> placesById = new HashMap<>();
        for (Long placeId : missingPlaceIds.stream().sorted().toList()) {
            mapPlaceRepository.findByIdForUpdate(placeId)
                    .ifPresent(mapPlace -> placesById.put(mapPlace.getId(), mapPlace));
        }

        Map<Long, PlaceRecommendationSnapshot> snapshotsAfterLock = new HashMap<>();
        for (PlaceRecommendationSnapshot snapshot : placeRecommendationSnapshotRepository.findByPlaceIdIn(missingPlaceIds)) {
            snapshotsAfterLock.put(snapshot.getPlaceId(), snapshot);
        }

        Map<Long, PlaceRecommendationSnapshot> createdSnapshots = new HashMap<>();
        for (Long placeId : missingPlaceIds) {
            PlaceRecommendationSnapshot existingSnapshot = snapshotsAfterLock.get(placeId);
            if (existingSnapshot != null) {
                createdSnapshots.put(placeId, existingSnapshot);
                continue;
            }

            MapPlace mapPlace = placesById.get(placeId);
            if (mapPlace == null) {
                throw new MapException(MapErrorCode.PLACE_NOT_FOUND);
            }
            createdSnapshots.put(placeId, createSnapshot(mapPlace, now));
        }
        return createdSnapshots;
    }

    private PlaceRecommendationSnapshot loadOrCreateSnapshot(Long placeId, LocalDateTime now) {
        PlaceRecommendationSnapshot existingSnapshot = placeRecommendationSnapshotRepository.findById(placeId)
                .orElse(null);
        if (existingSnapshot != null) {
            return existingSnapshot;
        }

        MapPlace mapPlace = mapPlaceRepository.findByIdForUpdate(placeId)
                .orElseThrow(() -> new MapException(MapErrorCode.PLACE_NOT_FOUND));

        PlaceRecommendationSnapshot snapshotAfterLock = placeRecommendationSnapshotRepository.findById(placeId)
                .orElse(null);
        if (snapshotAfterLock != null) {
            return snapshotAfterLock;
        }

        return createSnapshot(mapPlace, now);
    }

    private PlaceRecommendationSnapshot createSnapshot(MapPlace mapPlace, LocalDateTime now) {
        return PlaceRecommendationSnapshot.builder()
                .placeId(mapPlace.getId())
                .photoCount(mapPlace.currentPhotoCount())
                .bookmarkCount(mapBookmarkRepository.countByPlaceId(mapPlace.getId()))
                .totalLikeCount(mapImageRepository.sumLikeCountByPlaceId(mapPlace.getId()))
                .clickCount(0L)
                .bookmarkConversionCount(0L)
                .likeConversionCount(0L)
                .exposureCount(0L)
                .latestPostCreatedAt(mapImageRepository.findLatestCreatedAtByPlaceId(mapPlace.getId()))
                .updatedAt(now)
                .build();
    }

    private enum CountType {
        CLICK,
        EXPOSURE
    }
}
