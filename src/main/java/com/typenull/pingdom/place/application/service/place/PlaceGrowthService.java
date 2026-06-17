package com.typenull.pingdom.place.application.service.place;

import com.typenull.pingdom.place.domain.place.MapPlace;
import com.typenull.pingdom.place.domain.place.PlaceGrowthSnapshot;
import com.typenull.pingdom.place.domain.place.PlaceLevelPolicy;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlaceGrowthService {

    private final MapPlaceRepository mapPlaceRepository;

    @Transactional
    public PlaceGrowthSnapshot increasePhotoCount(Long placeId) {
        MapPlace mapPlace = getPlaceForUpdate(placeId);
        return increasePhotoCount(mapPlace);
    }

    @Transactional
    public PlaceGrowthSnapshot decreasePhotoCount(Long placeId) {
        MapPlace mapPlace = getPlaceForUpdate(placeId);
        return decreasePhotoCount(mapPlace);
    }

    public PlaceGrowthSnapshot increasePhotoCount(MapPlace mapPlace) {
        mapPlace.increasePhotoCount();
        return snapshot(mapPlace);
    }

    public PlaceGrowthSnapshot decreasePhotoCount(MapPlace mapPlace) {
        mapPlace.decreasePhotoCount();
        return snapshot(mapPlace);
    }

    @Transactional
    public MapPlace getPlaceForUpdate(Long placeId) {
        return mapPlaceRepository.findByIdForUpdate(placeId)
                .orElseThrow(() -> new MapException(MapErrorCode.PLACE_NOT_FOUND));
    }

    public PlaceGrowthSnapshot snapshot(MapPlace mapPlace) {
        if (mapPlace == null) {
            return null;
        }
        return PlaceLevelPolicy.snapshot(mapPlace.currentPhotoCount());
    }
}
