package com.typenull.pingdom.place.application.service;

import com.typenull.pingdom.place.domain.PlaceRecommendationExposure;
import com.typenull.pingdom.place.infrastructure.persistence.PlaceRecommendationExposureRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaceRecommendationExposureService {

    private final PlaceRecommendationExposureRepository placeRecommendationExposureRepository;
    private final PlaceRecommendationSnapshotService placeRecommendationSnapshotService;
    private final PlaceRecommendationVersionSnapshotService placeRecommendationVersionSnapshotService;

    public Map<Long, Long> loadExposureCounts(Collection<Long> placeIds) {
        if (placeIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, Long> exposureCounts = new HashMap<>();
        for (PlaceRecommendationExposureRepository.PlaceExposureCountProjection projection :
                placeRecommendationExposureRepository.countExposuresByPlaceIds(placeIds)) {
            exposureCounts.put(projection.getPlaceId(), projection.getExposureCount());
        }
        return Map.copyOf(exposureCounts);
    }

    public long countAllExposures() {
        return placeRecommendationExposureRepository.count();
    }

    @Transactional
    public void recordExposures(
            Long userId,
            double latitude,
            double longitude,
            String requestId,
            List<Long> placeIds,
            String recommendationVersion
    ) {
        if (placeIds.isEmpty()) {
            return;
        }

        List<PlaceRecommendationExposure> exposures = new ArrayList<>(placeIds.size());
        int ranking = 1;

        for (Long placeId : placeIds) {
            exposures.add(PlaceRecommendationExposure.builder()
                    .placeId(placeId)
                    .userId(userId)
                    .requestLatitude(latitude)
                    .requestLongitude(longitude)
                    .ranking(ranking++)
                    .recommendationVersion(recommendationVersion)
                    .requestId(requestId)
                    .build());
        }

        placeRecommendationExposureRepository.saveAll(exposures);
        placeRecommendationSnapshotService.increaseExposureCounts(placeIds);
        placeRecommendationVersionSnapshotService.increaseExposureCounts(placeIds, recommendationVersion);
    }
}
