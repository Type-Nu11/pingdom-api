package com.typenull.pingdom.place.application.service.recommendation.query;

import com.typenull.pingdom.place.application.service.recommendation.feedback.PlaceRecommendationClickService;
import com.typenull.pingdom.place.application.service.recommendation.feedback.PlaceRecommendationExposureService;

import com.typenull.pingdom.place.infrastructure.persistence.place.MapBookmarkRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationSnapshotRepository;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class PlaceRecommendationAggregateLoader {

    private final MapBookmarkRepository mapBookmarkRepository;
    private final MapImageRepository mapImageRepository;
    private final PlaceRecommendationSnapshotRepository placeRecommendationSnapshotRepository;
    private final PlaceRecommendationClickService placeRecommendationClickService;
    private final PlaceRecommendationExposureService placeRecommendationExposureService;

    Map<Long, PlaceAggregate> loadAggregates(List<PlaceDistance> candidates) {
        List<Long> placeIds = candidates.stream()
                .map(candidate -> candidate.place().getId())
                .toList();

        Map<Long, PlaceAggregate> aggregateMap = new HashMap<>();
        Set<Long> missingPlaceIds = new HashSet<>(placeIds);

        for (var snapshot : placeRecommendationSnapshotRepository.findByPlaceIdIn(placeIds)) {
            aggregateMap.put(snapshot.getPlaceId(), PlaceAggregate.fromSnapshot(snapshot));
            missingPlaceIds.remove(snapshot.getPlaceId());
        }

        if (missingPlaceIds.isEmpty()) {
            return aggregateMap;
        }

        for (MapBookmarkRepository.PlaceBookmarkCountProjection projection :
                mapBookmarkRepository.findBookmarkCountsByPlaceIds(missingPlaceIds)) {
            aggregateMap.computeIfAbsent(projection.getPlaceId(), ignored -> PlaceAggregate.empty())
                    .bookmarkCount = projection.getBookmarkCount();
        }

        for (MapImageRepository.PlaceImageAggregateProjection projection :
                mapImageRepository.findPlaceAggregatesByPlaceIds(missingPlaceIds)) {
            aggregateMap.computeIfAbsent(projection.getPlaceId(), ignored -> PlaceAggregate.empty())
                    .mergeImageAggregate(projection.getLikeSum(), projection.getLatestCreatedAt());
        }

        Map<Long, Long> exposureCounts = placeRecommendationExposureService.loadExposureCounts(missingPlaceIds);
        for (Map.Entry<Long, Long> exposureCountEntry : exposureCounts.entrySet()) {
            aggregateMap.computeIfAbsent(exposureCountEntry.getKey(), ignored -> PlaceAggregate.empty())
                    .exposureCount = exposureCountEntry.getValue();
        }

        Map<Long, Long> clickCounts = placeRecommendationClickService.loadClickCounts(missingPlaceIds);
        for (Map.Entry<Long, Long> clickCountEntry : clickCounts.entrySet()) {
            aggregateMap.computeIfAbsent(clickCountEntry.getKey(), ignored -> PlaceAggregate.empty())
                    .clickCount = clickCountEntry.getValue();
        }

        return aggregateMap;
    }

    long resolveTotalClickCount() {
        Long snapshotClickCount = placeRecommendationSnapshotRepository.sumClickCount();
        if (snapshotClickCount != null) {
            return snapshotClickCount;
        }
        return placeRecommendationClickService.countAllClicks();
    }

    long resolveTotalExposureCount() {
        Long snapshotExposureCount = placeRecommendationSnapshotRepository.sumExposureCount();
        if (snapshotExposureCount != null) {
            return snapshotExposureCount;
        }
        return placeRecommendationExposureService.countAllExposures();
    }
}
