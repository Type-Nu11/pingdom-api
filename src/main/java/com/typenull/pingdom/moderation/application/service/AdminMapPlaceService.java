package com.typenull.pingdom.moderation.application.service;

import com.typenull.pingdom.place.application.service.recommendation.PlaceRecommendationSnapshotResyncService;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminMapPlaceService {

    private final MapPlaceRepository mapPlaceRepository;
    private final PlaceRecommendationSnapshotResyncService placeRecommendationSnapshotResyncService;

    @Transactional
    public void deletePlace(long placeId) {
        boolean exists = mapPlaceRepository.existsById(placeId);
        if (!exists) {
            throw new MapException(MapErrorCode.PLACE_NOT_FOUND);
        }
        mapPlaceRepository.deleteById(placeId);
    }

    @Transactional
    public PlaceRecommendationSnapshotResyncService.SnapshotResyncResult resyncRecommendationSnapshots() {
        return placeRecommendationSnapshotResyncService.resyncAll();
    }
}
