package com.typenull.pingdom.domain.map.service;

import com.typenull.pingdom.domain.map.domain.MapPlace;
import com.typenull.pingdom.domain.map.dto.PlaceCreateRequest;
import com.typenull.pingdom.domain.map.exception.MapErrorCode;
import com.typenull.pingdom.domain.map.exception.MapException;
import com.typenull.pingdom.domain.map.repository.MapPlaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MapPlaceService {

    private final MapPlaceRepository mapPlaceRepository;

    @Transactional
    public Long createPlace(PlaceCreateRequest request, long userId) {
        MapPlace mapPlace = MapPlace.builder()
                .name(request.name())
                .address(request.address())
                .latitude(request.latitude())
                .longitude(request.longitude())
                .userId(userId)
                .build();

        return mapPlaceRepository.save(mapPlace).getId();
    }

    @Transactional
    public void deletePlace(long placeId, long userId) {
        MapPlace mapPlace = mapPlaceRepository.findById(placeId)
                .orElseThrow(() -> new MapException(MapErrorCode.PLACE_NOT_FOUND));

        if (!mapPlace.getUserId().equals(userId)) {
            throw new MapException(MapErrorCode.OTHERS_PLACE_NOT_DELETED);
        }

        mapPlaceRepository.delete(mapPlace);
    }
}

