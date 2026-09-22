package com.typenull.pingdom.place.application.service.place;

import com.typenull.pingdom.place.domain.place.core.MapPlace;
import com.typenull.pingdom.place.domain.place.statistics.PlaceGrowthSnapshot;
import com.typenull.pingdom.place.domain.place.category.PlaceLevelPolicy;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사진 수 변경을 장소 성장 단계 스냅샷으로 변환.
 * ID를 받는 변경 메서드는 장소 행을 잠그지만 엔티티를 직접 받는 메서드는 호출자의 잠금·트랜잭션에 의존.
 */
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
