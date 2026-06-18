package com.typenull.pingdom.place.application.service.recommendation;

import com.typenull.pingdom.place.domain.place.MapPlace;
import com.typenull.pingdom.place.domain.recommendation.PlaceRecommendationConversionType;
import com.typenull.pingdom.place.domain.recommendation.PlaceRecommendationSnapshot;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationClickRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapBookmarkRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationConversionRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationExposureRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationSnapshotRepository;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlaceRecommendationSnapshotResyncService {

    private static final int RESYNC_BATCH_SIZE = 500;

    private final MapPlaceRepository mapPlaceRepository;
    private final MapBookmarkRepository mapBookmarkRepository;
    private final MapImageRepository mapImageRepository;
    private final PlaceRecommendationClickRepository placeRecommendationClickRepository;
    private final PlaceRecommendationConversionRepository placeRecommendationConversionRepository;
    private final PlaceRecommendationExposureRepository placeRecommendationExposureRepository;
    private final PlaceRecommendationSnapshotRepository placeRecommendationSnapshotRepository;
    private final PlaceSimilaritySnapshotResyncService placeSimilaritySnapshotResyncService;
    private final PlaceRecommendationVersionSnapshotService placeRecommendationVersionSnapshotService;

    @Transactional
    public SnapshotResyncResult resyncAll() {
        long placeCount = mapPlaceRepository.count();
        if (placeCount == 0L) {
            long deletedSnapshotCount = placeRecommendationSnapshotRepository.count();
            if (deletedSnapshotCount > 0L) {
                placeRecommendationSnapshotRepository.deleteAllInBatch();
            }
            PlaceRecommendationVersionSnapshotService.VersionSnapshotResyncResult versionResult =
                    placeRecommendationVersionSnapshotService.resyncAll();
            PlaceSimilaritySnapshotResyncService.SimilaritySnapshotResyncResult similarityResult =
                    placeSimilaritySnapshotResyncService.resyncAll();
            return new SnapshotResyncResult(
                    0,
                    0,
                    deletedSnapshotCount,
                    similarityResult.synchronizedSnapshotCount(),
                    similarityResult.deletedSnapshotCount(),
                    versionResult.synchronizedSnapshotCount(),
                    versionResult.deletedSnapshotCount()
            );
        }

        LocalDateTime syncedAt = LocalDateTime.now();
        Set<Long> activePlaceIds = new HashSet<>();
        long synchronizedSnapshotCount = 0L;
        int pageNumber = 0;

        while (true) {
            Page<MapPlace> placePage = mapPlaceRepository.findAll(
                    PageRequest.of(pageNumber, RESYNC_BATCH_SIZE, Sort.by(Sort.Order.asc("id")))
            );
            if (placePage.isEmpty()) {
                break;
            }

            List<MapPlace> places = placePage.getContent();
            List<Long> placeIds = places.stream()
                    .map(MapPlace::getId)
                    .toList();
            activePlaceIds.addAll(placeIds);

            Map<Long, Long> bookmarkCounts = loadBookmarkCounts(placeIds);
            Map<Long, ImageAggregate> imageAggregates = loadImageAggregates(placeIds);
            Map<Long, Long> clickCounts = loadClickCounts(placeIds);
            Map<Long, ConversionCounts> conversionCounts = loadConversionCounts(placeIds);
            Map<Long, Long> exposureCounts = loadExposureCounts(placeIds);
            Map<Long, PlaceRecommendationSnapshot> existingSnapshotsByPlaceId = new HashMap<>();
            for (PlaceRecommendationSnapshot existingSnapshot :
                    placeRecommendationSnapshotRepository.findByPlaceIdIn(placeIds)) {
                existingSnapshotsByPlaceId.put(existingSnapshot.getPlaceId(), existingSnapshot);
            }

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
            synchronizedSnapshotCount += snapshotsToSave.size();

            if (!placePage.hasNext()) {
                break;
            }
            pageNumber++;
        }

        List<Long> orphanSnapshotPlaceIds = collectOrphanSnapshotPlaceIds(activePlaceIds);

        if (!orphanSnapshotPlaceIds.isEmpty()) {
            placeRecommendationSnapshotRepository.deleteAllByIdInBatch(orphanSnapshotPlaceIds);
        }

        PlaceSimilaritySnapshotResyncService.SimilaritySnapshotResyncResult similarityResult =
                placeSimilaritySnapshotResyncService.resyncAll();
        PlaceRecommendationVersionSnapshotService.VersionSnapshotResyncResult versionResult =
                placeRecommendationVersionSnapshotService.resyncAll();

        return new SnapshotResyncResult(
                placeCount,
                synchronizedSnapshotCount,
                orphanSnapshotPlaceIds.size(),
                similarityResult.synchronizedSnapshotCount(),
                similarityResult.deletedSnapshotCount(),
                versionResult.synchronizedSnapshotCount(),
                versionResult.deletedSnapshotCount()
        );
    }

    private List<Long> collectOrphanSnapshotPlaceIds(Set<Long> activePlaceIds) {
        List<Long> orphanSnapshotPlaceIds = new ArrayList<>();
        int pageNumber = 0;

        while (true) {
            Page<PlaceRecommendationSnapshot> snapshotPage = placeRecommendationSnapshotRepository.findAll(
                    PageRequest.of(pageNumber, RESYNC_BATCH_SIZE, Sort.by(Sort.Order.asc("placeId")))
            );
            if (snapshotPage.isEmpty()) {
                break;
            }

            for (PlaceRecommendationSnapshot snapshot : snapshotPage.getContent()) {
                if (!activePlaceIds.contains(snapshot.getPlaceId())) {
                    orphanSnapshotPlaceIds.add(snapshot.getPlaceId());
                }
            }

            if (!snapshotPage.hasNext()) {
                break;
            }
            pageNumber++;
        }

        return orphanSnapshotPlaceIds;
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
            long deletedSnapshotCount,
            long synchronizedSimilaritySnapshotCount,
            long deletedSimilaritySnapshotCount,
            long synchronizedVersionSnapshotCount,
            long deletedVersionSnapshotCount
    ) {
    }
}
