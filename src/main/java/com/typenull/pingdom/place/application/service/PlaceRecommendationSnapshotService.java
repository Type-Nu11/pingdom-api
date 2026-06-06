package com.typenull.pingdom.place.application.service;

import com.typenull.pingdom.place.domain.MapPlace;
import com.typenull.pingdom.place.domain.PlaceRecommendationConversionType;
import com.typenull.pingdom.place.domain.PlaceRecommendationSnapshot;
import com.typenull.pingdom.place.infrastructure.persistence.MapBookmarkRepository;
import com.typenull.pingdom.place.infrastructure.persistence.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.PlaceRecommendationSnapshotRepository;
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

        List<PlaceRecommendationSnapshot> snapshotsToSave = new ArrayList<>(increments.size());
        for (Map.Entry<Long, Long> incrementEntry : increments.entrySet()) {
            PlaceRecommendationSnapshot snapshot = existingSnapshots.get(incrementEntry.getKey());
            if (snapshot == null) {
                snapshot = loadOrCreateSnapshot(incrementEntry.getKey(), now);
            }

            if (countType == CountType.CLICK) {
                snapshot.increaseClickCount(incrementEntry.getValue(), now);
            } else {
                snapshot.increaseExposureCount(incrementEntry.getValue(), now);
            }
            snapshotsToSave.add(snapshot);
        }

        placeRecommendationSnapshotRepository.saveAll(snapshotsToSave);
    }

    private PlaceRecommendationSnapshot loadOrCreateSnapshot(Long placeId, LocalDateTime now) {
        PlaceRecommendationSnapshot existingSnapshot = placeRecommendationSnapshotRepository.findById(placeId)
                .orElse(null);
        if (existingSnapshot != null) {
            return existingSnapshot;
        }

        MapPlace mapPlace = mapPlaceRepository.findById(placeId)
                .orElseThrow(() -> new MapException(MapErrorCode.PLACE_NOT_FOUND));

        return PlaceRecommendationSnapshot.builder()
                .placeId(placeId)
                .photoCount(mapPlace.currentPhotoCount())
                .bookmarkCount(mapBookmarkRepository.countByPlaceId(placeId))
                .totalLikeCount(mapImageRepository.sumLikeCountByPlaceId(placeId))
                .clickCount(0L)
                .bookmarkConversionCount(0L)
                .likeConversionCount(0L)
                .exposureCount(0L)
                .latestPostCreatedAt(mapImageRepository.findLatestCreatedAtByPlaceId(placeId))
                .updatedAt(now)
                .build();
    }

    private enum CountType {
        CLICK,
        EXPOSURE
    }
}
