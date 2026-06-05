package com.typenull.pingdom.place.application.service;

import com.typenull.pingdom.place.domain.MapPlace;
import com.typenull.pingdom.place.domain.PlaceRecommendationSnapshot;
import com.typenull.pingdom.place.infrastructure.persistence.MapBookmarkRepository;
import com.typenull.pingdom.place.infrastructure.persistence.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.PlaceRecommendationSnapshotRepository;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import java.time.LocalDateTime;
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
        MapPlace mapPlace = mapPlaceRepository.findById(placeId)
                .orElseThrow(() -> new MapException(MapErrorCode.PLACE_NOT_FOUND));

        long photoCount = mapPlace.currentPhotoCount();
        long bookmarkCount = mapBookmarkRepository.countByPlaceId(placeId);
        long totalLikeCount = mapImageRepository.sumLikeCountByPlaceId(placeId);
        LocalDateTime latestPostCreatedAt = mapImageRepository.findLatestCreatedAtByPlaceId(placeId);
        LocalDateTime now = LocalDateTime.now();

        PlaceRecommendationSnapshot snapshot = placeRecommendationSnapshotRepository.findById(placeId)
                .orElseGet(() -> PlaceRecommendationSnapshot.builder()
                        .placeId(placeId)
                        .updatedAt(now)
                        .build());

        snapshot.synchronize(photoCount, bookmarkCount, totalLikeCount, latestPostCreatedAt, now);
        placeRecommendationSnapshotRepository.save(snapshot);
    }

    @Transactional
    public void delete(Long placeId) {
        placeRecommendationSnapshotRepository.deleteById(placeId);
    }
}
