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

    public ExposureMetrics loadExposureMetrics(Collection<Long> placeIds) {
        if (placeIds.isEmpty()) {
            return ExposureMetrics.empty();
        }

        Map<Long, Long> exposureCounts = new HashMap<>();
        for (PlaceRecommendationExposureRepository.PlaceExposureCountProjection projection :
                placeRecommendationExposureRepository.countExposuresByPlaceIds(placeIds)) {
            exposureCounts.put(projection.getPlaceId(), projection.getExposureCount());
        }

        return new ExposureMetrics(Map.copyOf(exposureCounts), placeRecommendationExposureRepository.count());
    }

    @Transactional
    public void recordExposures(Long userId, double latitude, double longitude, List<Long> placeIds) {
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
                    .build());
        }

        placeRecommendationExposureRepository.saveAll(exposures);
    }

    public record ExposureMetrics(
            Map<Long, Long> exposureCounts,
            long totalExposureCount
    ) {
        private static ExposureMetrics empty() {
            return new ExposureMetrics(Map.of(), 0L);
        }

        public long exposureCountOf(Long placeId) {
            return exposureCounts.getOrDefault(placeId, 0L);
        }
    }
}
