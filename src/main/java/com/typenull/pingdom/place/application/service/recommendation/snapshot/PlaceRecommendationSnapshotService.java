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
 * 기존 행·최초 생성·refresh 모두 같은 장소 쓰기 잠금 후 최신 snapshot을 조회하여 갱신.
 */
@Service
@RequiredArgsConstructor
public class PlaceRecommendationSnapshotService {

    private final PlaceRecommendationSnapshotRepository placeRecommendationSnapshotRepository;
    private final MapPlaceRepository mapPlaceRepository;
    private final MapBookmarkRepository mapBookmarkRepository;
    private final MapImageRepository mapImageRepository;
    private final PlaceRecommendationSnapshotWriteLock snapshotWriteLock;

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

        snapshot.synchronizeContent(
                mapPlace.currentPhotoCount(),
                mapBookmarkRepository.countByPlaceId(placeId),
                mapImageRepository.sumLikeCountByPlaceId(placeId),
                mapImageRepository.findLatestCreatedAtByPlaceId(placeId),
                now
        );
        placeRecommendationSnapshotRepository.save(snapshot);
    }

    @Transactional
    public void delete(Long placeId) {
        snapshotWriteLock.lockPlace(placeId);
        snapshotWriteLock.reloadSnapshot(() -> placeRecommendationSnapshotRepository.findById(placeId))
                .ifPresent(placeRecommendationSnapshotRepository::delete);
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
     * 장소 잠금은 원천 로그와 전체·버전 snapshot 기록이 커밋될 때까지 유지.
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

        Map<Long, MapPlace> placesById = new HashMap<>();
        for (MapPlace place : snapshotWriteLock.requirePlaces(increments.keySet())) {
            placesById.put(place.getId(), place);
        }
        LocalDateTime now = LocalDateTime.now();
        Map<Long, PlaceRecommendationSnapshot> snapshotsById = new HashMap<>();
        for (PlaceRecommendationSnapshot snapshot : snapshotWriteLock.reloadSnapshots(
                () -> placeRecommendationSnapshotRepository.findByPlaceIdIn(increments.keySet()))) {
            snapshotsById.put(snapshot.getPlaceId(), snapshot);
        }

        List<PlaceRecommendationSnapshot> snapshotsToSave = new ArrayList<>(increments.size());
        for (Long placeId : increments.keySet().stream().sorted().toList()) {
            PlaceRecommendationSnapshot snapshot = snapshotsById.get(placeId);
            if (snapshot == null) {
                snapshot = createSnapshot(placesById.get(placeId), now);
            }
            if (countType == CountType.CLICK) {
                snapshot.increaseClickCount(increments.get(placeId), now);
            } else {
                snapshot.increaseExposureCount(increments.get(placeId), now);
            }
            snapshotsToSave.add(snapshot);
        }
        placeRecommendationSnapshotRepository.saveAll(snapshotsToSave);
    }

    private PlaceRecommendationSnapshot loadOrCreateSnapshot(Long placeId, LocalDateTime now) {
        MapPlace place = snapshotWriteLock.requirePlace(placeId);
        return snapshotWriteLock.reloadSnapshot(() -> placeRecommendationSnapshotRepository.findById(placeId))
                .orElseGet(() -> createSnapshot(place, now));
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
