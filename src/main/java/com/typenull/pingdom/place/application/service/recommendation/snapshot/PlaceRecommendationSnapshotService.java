package com.typenull.pingdom.place.application.service.recommendation.snapshot;

import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.recommendation.engagement.PlaceRecommendationConversionType;
import com.typenull.pingdom.place.domain.recommendation.snapshot.PlaceRecommendationSnapshot;
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

/**
 * 장소 콘텐츠 집계를 갱신하고 추천 클릭·노출·전환을 증분 반영.
 * 스냅샷 최초 생성은 장소 행 잠금 후 재조회하지만 기존 스냅샷의 증가 경로는 별도 쓰기 잠금이나 version 검사가 없음.
 * 따라서 최초 생성 조정과 기존 집계의 동시 증가 보장 범위는 구분 필요.
 */
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

    /**
     * 사진·북마크·좋아요·최신 게시 시각만 원본에서 갱신하고 기존 클릭·노출·전환 집계는 유지.
     * 원본 추천 이벤트까지 다시 세어야 할 때는 별도 재동기화 서비스를 사용.
     */
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

    /**
     * 장소 스냅샷을 읽거나 처음 생성하고 BOOKMARK이면 북마크 전환, 그 외에는 좋아요 전환 수를 1 증가시킴.
     * 기존 스냅샷 경로는 일반 조회 후 갱신하므로 동시 증가 시 갱신 손실 가능.
     */
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

        List<Long> sortedMissingPlaceIds = missingPlaceIds.stream().sorted().toList();
        Map<Long, MapPlace> placesById = new HashMap<>();
        for (MapPlace mapPlace : mapPlaceRepository.findAllByIdInForUpdate(sortedMissingPlaceIds)) {
            placesById.put(mapPlace.getId(), mapPlace);
        }

        Map<Long, PlaceRecommendationSnapshot> snapshotsAfterLock = new HashMap<>();
        for (PlaceRecommendationSnapshot snapshot :
                placeRecommendationSnapshotRepository.findByPlaceIdInForReadLock(missingPlaceIds)) {
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

        PlaceRecommendationSnapshot snapshotAfterLock = placeRecommendationSnapshotRepository.findByPlaceIdForReadLock(placeId)
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
