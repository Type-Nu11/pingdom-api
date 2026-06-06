package com.typenull.pingdom.place.application.service;

import com.typenull.pingdom.place.domain.MapPlace;
import com.typenull.pingdom.place.domain.PlaceRecommendationConversionType;
import com.typenull.pingdom.place.domain.PlaceRecommendationSnapshot;
import com.typenull.pingdom.place.infrastructure.persistence.PlaceRecommendationClickRepository;
import com.typenull.pingdom.place.infrastructure.persistence.MapBookmarkRepository;
import com.typenull.pingdom.place.infrastructure.persistence.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.PlaceRecommendationConversionRepository;
import com.typenull.pingdom.place.infrastructure.persistence.PlaceRecommendationExposureRepository;
import com.typenull.pingdom.place.infrastructure.persistence.PlaceRecommendationSnapshotRepository;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlaceRecommendationSnapshotResyncService {

    private final MapPlaceRepository mapPlaceRepository;
    private final MapBookmarkRepository mapBookmarkRepository;
    private final MapImageRepository mapImageRepository;
    private final PlaceRecommendationClickRepository placeRecommendationClickRepository;
    private final PlaceRecommendationConversionRepository placeRecommendationConversionRepository;
    private final PlaceRecommendationExposureRepository placeRecommendationExposureRepository;
    private final PlaceRecommendationSnapshotRepository placeRecommendationSnapshotRepository;

    @Transactional
    public SnapshotResyncResult resyncAll() {
        List<MapPlace> places = mapPlaceRepository.findAll();
        List<Long> placeIds = places.stream()
                .map(MapPlace::getId)
                .toList();

        List<PlaceRecommendationSnapshot> existingSnapshots = placeRecommendationSnapshotRepository.findAll();
        if (placeIds.isEmpty()) {
            long deletedSnapshotCount = existingSnapshots.size();
            if (deletedSnapshotCount > 0L) {
                placeRecommendationSnapshotRepository.deleteAllInBatch();
            }
            return new SnapshotResyncResult(0, 0, deletedSnapshotCount);
        }

        Map<Long, Long> bookmarkCounts = loadBookmarkCounts(placeIds);
        Map<Long, ImageAggregate> imageAggregates = loadImageAggregates(placeIds);
        Map<Long, Long> clickCounts = loadClickCounts(placeIds);
        Map<Long, ConversionCounts> conversionCounts = loadConversionCounts(placeIds);
        Map<Long, Long> exposureCounts = loadExposureCounts(placeIds);
        Map<Long, PlaceRecommendationSnapshot> existingSnapshotsByPlaceId = new HashMap<>();
        for (PlaceRecommendationSnapshot existingSnapshot : existingSnapshots) {
            existingSnapshotsByPlaceId.put(existingSnapshot.getPlaceId(), existingSnapshot);
        }

        LocalDateTime syncedAt = LocalDateTime.now();
        List<PlaceRecommendationSnapshot> snapshotsToSave = new ArrayList<>(places.size());

        for (MapPlace place : places) {
            Long placeId = place.getId();
            ImageAggregate imageAggregate = imageAggregates.getOrDefault(placeId, ImageAggregate.empty());

            PlaceRecommendationSnapshot snapshot = existingSnapshotsByPlaceId.get(placeId);
            if (snapshot == null) {
                snapshot = PlaceRecommendationSnapshot.builder()
                        .placeId(placeId)
                        .updatedAt(syncedAt)
                        .build();
            }

            snapshot.synchronize(
                    place.currentPhotoCount(),
                    bookmarkCounts.getOrDefault(placeId, 0L),
                    imageAggregate.totalLikeCount(),
                    clickCounts.getOrDefault(placeId, 0L),
                    conversionCounts.getOrDefault(placeId, ConversionCounts.empty()).bookmarkConversionCount(),
                    conversionCounts.getOrDefault(placeId, ConversionCounts.empty()).likeConversionCount(),
                    exposureCounts.getOrDefault(placeId, 0L),
                    imageAggregate.latestPostCreatedAt(),
                    syncedAt
            );
            snapshotsToSave.add(snapshot);
        }

        placeRecommendationSnapshotRepository.saveAll(snapshotsToSave);

        Set<Long> placeIdSet = new HashSet<>(placeIds);
        List<Long> orphanSnapshotPlaceIds = existingSnapshots.stream()
                .map(PlaceRecommendationSnapshot::getPlaceId)
                .filter(snapshotPlaceId -> !placeIdSet.contains(snapshotPlaceId))
                .toList();

        if (!orphanSnapshotPlaceIds.isEmpty()) {
            placeRecommendationSnapshotRepository.deleteAllByIdInBatch(orphanSnapshotPlaceIds);
        }

        return new SnapshotResyncResult(
                placeIds.size(),
                snapshotsToSave.size(),
                orphanSnapshotPlaceIds.size()
        );
    }

    private Map<Long, Long> loadBookmarkCounts(List<Long> placeIds) {
        Map<Long, Long> bookmarkCounts = new HashMap<>();
        for (MapBookmarkRepository.PlaceBookmarkCountProjection projection :
                mapBookmarkRepository.findBookmarkCountsByPlaceIds(placeIds)) {
            bookmarkCounts.put(projection.getPlaceId(), projection.getBookmarkCount());
        }
        return bookmarkCounts;
    }

    private Map<Long, ImageAggregate> loadImageAggregates(List<Long> placeIds) {
        Map<Long, ImageAggregate> imageAggregates = new HashMap<>();
        for (MapImageRepository.PlaceImageAggregateProjection projection :
                mapImageRepository.findPlaceAggregatesByPlaceIds(placeIds)) {
            imageAggregates.put(
                    projection.getPlaceId(),
                    new ImageAggregate(
                            projection.getLikeSum() == null ? 0L : projection.getLikeSum(),
                            projection.getLatestCreatedAt()
                    )
            );
        }
        return imageAggregates;
    }

    private Map<Long, Long> loadExposureCounts(List<Long> placeIds) {
        Map<Long, Long> exposureCounts = new HashMap<>();
        for (PlaceRecommendationExposureRepository.PlaceExposureCountProjection projection :
                placeRecommendationExposureRepository.countExposuresByPlaceIds(placeIds)) {
            exposureCounts.put(projection.getPlaceId(), projection.getExposureCount());
        }
        return exposureCounts;
    }

    private Map<Long, Long> loadClickCounts(List<Long> placeIds) {
        Map<Long, Long> clickCounts = new HashMap<>();
        for (PlaceRecommendationClickRepository.PlaceClickCountProjection projection :
                placeRecommendationClickRepository.countClicksByPlaceIds(placeIds)) {
            clickCounts.put(projection.getPlaceId(), projection.getClickCount());
        }
        return clickCounts;
    }

    private Map<Long, ConversionCounts> loadConversionCounts(List<Long> placeIds) {
        Map<Long, ConversionCounts> conversionCounts = new HashMap<>();
        for (PlaceRecommendationConversionRepository.PlaceConversionCountProjection projection :
                placeRecommendationConversionRepository.countConversionsByPlaceIds(placeIds)) {
            ConversionCounts existing = conversionCounts.getOrDefault(projection.getPlaceId(), ConversionCounts.empty());
            if (projection.getConversionType() == PlaceRecommendationConversionType.BOOKMARK) {
                conversionCounts.put(
                        projection.getPlaceId(),
                        existing.withBookmarkConversionCount(projection.getConversionCount())
                );
                continue;
            }
            conversionCounts.put(
                    projection.getPlaceId(),
                    existing.withLikeConversionCount(projection.getConversionCount())
            );
        }
        return conversionCounts;
    }

    private record ImageAggregate(long totalLikeCount, LocalDateTime latestPostCreatedAt) {
        private static ImageAggregate empty() {
            return new ImageAggregate(0L, null);
        }
    }

    private record ConversionCounts(long bookmarkConversionCount, long likeConversionCount) {
        private static ConversionCounts empty() {
            return new ConversionCounts(0L, 0L);
        }

        private ConversionCounts withBookmarkConversionCount(long count) {
            return new ConversionCounts(count, likeConversionCount);
        }

        private ConversionCounts withLikeConversionCount(long count) {
            return new ConversionCounts(bookmarkConversionCount, count);
        }
    }

    public record SnapshotResyncResult(
            long placeCount,
            long synchronizedSnapshotCount,
            long deletedSnapshotCount
    ) {
    }
}
