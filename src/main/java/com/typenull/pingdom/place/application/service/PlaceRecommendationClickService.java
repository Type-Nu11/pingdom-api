package com.typenull.pingdom.place.application.service;

import com.typenull.pingdom.place.domain.PlaceRecommendationClick;
import com.typenull.pingdom.place.infrastructure.persistence.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.PlaceRecommendationClickRepository;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaceRecommendationClickService {

    private final PlaceRecommendationClickRepository placeRecommendationClickRepository;
    private final PlaceRecommendationSnapshotService placeRecommendationSnapshotService;
    private final PlaceRecommendationVersionSnapshotService placeRecommendationVersionSnapshotService;
    private final MapPlaceRepository mapPlaceRepository;

    @Transactional
    public void recordClick(Long userId, Long placeId, String recommendationVersion) {
        if (!mapPlaceRepository.existsById(placeId)) {
            throw new MapException(MapErrorCode.PLACE_NOT_FOUND);
        }

        placeRecommendationClickRepository.save(PlaceRecommendationClick.builder()
                .placeId(placeId)
                .userId(userId)
                .recommendationVersion(recommendationVersion)
                .build());

        placeRecommendationSnapshotService.increaseClickCounts(java.util.List.of(placeId));
        placeRecommendationVersionSnapshotService.increaseClickCounts(
                java.util.List.of(placeId),
                recommendationVersion
        );
    }

    public Map<Long, Long> loadClickCounts(Collection<Long> placeIds) {
        if (placeIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, Long> clickCounts = new HashMap<>();
        for (PlaceRecommendationClickRepository.PlaceClickCountProjection projection :
                placeRecommendationClickRepository.countClicksByPlaceIds(placeIds)) {
            clickCounts.put(projection.getPlaceId(), projection.getClickCount());
        }
        return Map.copyOf(clickCounts);
    }

    public long countAllClicks() {
        return placeRecommendationClickRepository.count();
    }
}
