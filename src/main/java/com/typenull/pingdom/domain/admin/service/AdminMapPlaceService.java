package com.typenull.pingdom.domain.admin.service;

import com.typenull.pingdom.domain.map.exception.MapErrorCode;
import com.typenull.pingdom.domain.map.exception.MapException;
import com.typenull.pingdom.place.domain.repository.MapPlaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminMapPlaceService {

    private final MapPlaceRepository mapPlaceRepository;

    @Transactional
    public void deletePlace(long placeId) {
        boolean exists = mapPlaceRepository.existsById(placeId);
        if (!exists) {
            throw new MapException(MapErrorCode.PLACE_NOT_FOUND);
        }
        mapPlaceRepository.deleteById(placeId);
    }
}

