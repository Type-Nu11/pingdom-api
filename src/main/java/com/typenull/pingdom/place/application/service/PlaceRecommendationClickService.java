package com.typenull.pingdom.place.application.service;

import com.typenull.pingdom.place.domain.PlaceRecommendationClick;
import com.typenull.pingdom.place.infrastructure.persistence.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.PlaceRecommendationClickRepository;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaceRecommendationClickService {

    private final PlaceRecommendationClickRepository placeRecommendationClickRepository;
    private final PlaceRecommendationSnapshotService placeRecommendationSnapshotService;
    private final MapPlaceRepository mapPlaceRepository;

    @Transactional
    public void recordClick(Long userId, Long placeId) {
        if (!mapPlaceRepository.existsById(placeId)) {
            throw new MapException(MapErrorCode.PLACE_NOT_FOUND);
        }

        placeRecommendationClickRepository.save(PlaceRecommendationClick.builder()
                .placeId(placeId)
                .userId(userId)
                .build());

        placeRecommendationSnapshotService.increaseClickCounts(java.util.List.of(placeId));
    }
}
