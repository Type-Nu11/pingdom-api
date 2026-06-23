package com.typenull.pingdom.moderation.application.service;

import com.typenull.pingdom.moderation.api.dto.place.duplicate.AdminMapPlaceMergeRequest;
import com.typenull.pingdom.moderation.api.dto.place.duplicate.AdminMapPlaceMergeResponse;
import com.typenull.pingdom.moderation.application.support.AdminPlaceDuplicateResolver;
import com.typenull.pingdom.moderation.domain.exception.AdminErrorCode;
import com.typenull.pingdom.moderation.domain.exception.AdminException;
import com.typenull.pingdom.place.application.service.recommendation.snapshot.PlaceRecommendationSnapshotResyncService;
import com.typenull.pingdom.place.domain.place.MapBookmark;
import com.typenull.pingdom.place.domain.place.MapPlace;
import com.typenull.pingdom.place.domain.recommendation.PlaceRecommendationConversion;
import com.typenull.pingdom.place.domain.recommendation.PlaceRecommendationConversionType;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapBookmarkRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationClickRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationConversionRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationExposureRepository;
import com.typenull.pingdom.place.infrastructure.persistence.recommendation.PlaceRecommendationFeatureLogRepository;
import com.typenull.pingdom.post.domain.MapImage;
import com.typenull.pingdom.post.infrastructure.persistence.MapImageRepository;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminMapPlaceService {

    private static final Logger log = LoggerFactory.getLogger(AdminMapPlaceService.class);

    private final MapPlaceRepository mapPlaceRepository;
    private final MapBookmarkRepository mapBookmarkRepository;
    private final MapImageRepository mapImageRepository;
    private final PlaceRecommendationClickRepository placeRecommendationClickRepository;
    private final PlaceRecommendationExposureRepository placeRecommendationExposureRepository;
    private final PlaceRecommendationConversionRepository placeRecommendationConversionRepository;
    private final PlaceRecommendationFeatureLogRepository placeRecommendationFeatureLogRepository;
    private final PlaceRecommendationSnapshotResyncService placeRecommendationSnapshotResyncService;
    private final AdminPlaceDuplicateResolver adminPlaceDuplicateResolver;

    @Transactional
    public void deletePlace(long placeId) {
        boolean exists = mapPlaceRepository.existsById(placeId);
        if (!exists) {
            throw new AdminException(AdminErrorCode.PLACE_NOT_FOUND);
        }
        mapPlaceRepository.deleteById(placeId);
    }

    @Transactional
    public AdminMapPlaceMergeResponse mergePlaces(Long adminUserId, AdminMapPlaceMergeRequest request) {
        validateMergeRequest(request);

        List<Long> orderedIds = List.of(request.sourcePlaceId(), request.targetPlaceId()).stream()
                .sorted(Comparator.naturalOrder())
                .toList();

        MapPlace firstLockedPlace = mapPlaceRepository.findByIdForUpdate(orderedIds.get(0))
                .orElseThrow(() -> new AdminException(AdminErrorCode.PLACE_NOT_FOUND));
        MapPlace secondLockedPlace = mapPlaceRepository.findByIdForUpdate(orderedIds.get(1))
                .orElseThrow(() -> new AdminException(AdminErrorCode.PLACE_NOT_FOUND));

        MapPlace sourcePlace = firstLockedPlace.getId().equals(request.sourcePlaceId()) ? firstLockedPlace : secondLockedPlace;
        MapPlace targetPlace = firstLockedPlace.getId().equals(request.targetPlaceId()) ? firstLockedPlace : secondLockedPlace;

        if (!adminPlaceDuplicateResolver.areDuplicates(sourcePlace, targetPlace)) {
            throw new AdminException(AdminErrorCode.PLACE_MERGE_NOT_ALLOWED);
        }

        long movedImageCount = reassignImages(sourcePlace, targetPlace);
        BookmarkMergeResult bookmarkMergeResult = reassignBookmarks(sourcePlace, targetPlace);
        ConversionMergeResult conversionMergeResult = reassignConversions(sourcePlace, targetPlace);
        int movedClickCount = placeRecommendationClickRepository.updatePlaceId(sourcePlace.getId(), targetPlace.getId());
        int movedExposureCount = placeRecommendationExposureRepository.updatePlaceId(sourcePlace.getId(), targetPlace.getId());
        int movedFeatureLogCount = placeRecommendationFeatureLogRepository.updatePlaceId(sourcePlace.getId(), targetPlace.getId());

        targetPlace.replacePhotoCount(mapImageRepository.countByMapPlace_Id(targetPlace.getId()));
        mapPlaceRepository.delete(sourcePlace);
        placeRecommendationSnapshotResyncService.resyncMergedPlace(sourcePlace.getId(), targetPlace.getId());

        log.info(
                "Admin place merge completed. adminUserId={}, sourcePlaceId={}, targetPlaceId={}, movedImageCount={}, movedBookmarkCount={}, removedBookmarkCount={}, movedConversionCount={}, removedConversionCount={}, movedClickCount={}, movedExposureCount={}, movedFeatureLogCount={}",
                adminUserId,
                sourcePlace.getId(),
                targetPlace.getId(),
                movedImageCount,
                bookmarkMergeResult.movedCount(),
                bookmarkMergeResult.deletedCount(),
                conversionMergeResult.movedCount(),
                conversionMergeResult.deletedCount(),
                movedClickCount,
                movedExposureCount,
                movedFeatureLogCount
        );

        return new AdminMapPlaceMergeResponse(
                sourcePlace.getId(),
                targetPlace.getId(),
                "중복 장소를 병합했습니다."
        );
    }

    @Transactional
    public PlaceRecommendationSnapshotResyncService.SnapshotResyncResult resyncRecommendationSnapshots() {
        return placeRecommendationSnapshotResyncService.resyncAll();
    }

    private void validateMergeRequest(AdminMapPlaceMergeRequest request) {
        if (request == null || request.sourcePlaceId() == null || request.targetPlaceId() == null) {
            throw new AdminException(AdminErrorCode.PLACE_MERGE_INVALID_REQUEST);
        }
        if (request.sourcePlaceId().equals(request.targetPlaceId())) {
            throw new AdminException(AdminErrorCode.PLACE_MERGE_INVALID_REQUEST);
        }
    }

    private long reassignImages(MapPlace sourcePlace, MapPlace targetPlace) {
        List<MapImage> sourceImages = mapImageRepository.findByMapPlace_Id(sourcePlace.getId());
        sourceImages.forEach(image -> image.reassignPlace(targetPlace));
        return sourceImages.size();
    }

    private BookmarkMergeResult reassignBookmarks(MapPlace sourcePlace, MapPlace targetPlace) {
        List<MapBookmark> sourceBookmarks = mapBookmarkRepository.findByPlaceId(sourcePlace.getId());
        Set<Long> targetBookmarkUserIds = new HashSet<>();
        for (MapBookmark targetBookmark : mapBookmarkRepository.findByPlaceId(targetPlace.getId())) {
            targetBookmarkUserIds.add(targetBookmark.getUserId());
        }

        int movedCount = 0;
        int deletedCount = 0;
        for (MapBookmark sourceBookmark : sourceBookmarks) {
            if (targetBookmarkUserIds.contains(sourceBookmark.getUserId())) {
                mapBookmarkRepository.delete(sourceBookmark);
                deletedCount++;
                continue;
            }
            sourceBookmark.reassignPlace(targetPlace.getId());
            targetBookmarkUserIds.add(sourceBookmark.getUserId());
            movedCount++;
        }
        return new BookmarkMergeResult(movedCount, deletedCount);
    }

    private ConversionMergeResult reassignConversions(MapPlace sourcePlace, MapPlace targetPlace) {
        List<PlaceRecommendationConversion> sourceConversions =
                placeRecommendationConversionRepository.findByPlaceId(sourcePlace.getId());
        List<PlaceRecommendationConversion> targetConversions =
                placeRecommendationConversionRepository.findByPlaceId(targetPlace.getId());

        record ConversionKey(Long userId, PlaceRecommendationConversionType conversionType) {
        }

        Set<ConversionKey> targetConversionKeys = new HashSet<>();
        for (PlaceRecommendationConversion targetConversion : targetConversions) {
            targetConversionKeys.add(new ConversionKey(
                    targetConversion.getUserId(),
                    targetConversion.getConversionType()
            ));
        }

        int movedCount = 0;
        int deletedCount = 0;
        for (PlaceRecommendationConversion sourceConversion : sourceConversions) {
            ConversionKey conversionKey = new ConversionKey(
                    sourceConversion.getUserId(),
                    sourceConversion.getConversionType()
            );
            if (targetConversionKeys.contains(conversionKey)) {
                placeRecommendationConversionRepository.delete(sourceConversion);
                deletedCount++;
                continue;
            }
            sourceConversion.reassignPlace(targetPlace.getId());
            targetConversionKeys.add(conversionKey);
            movedCount++;
        }
        return new ConversionMergeResult(movedCount, deletedCount);
    }

    private record BookmarkMergeResult(int movedCount, int deletedCount) {
    }

    private record ConversionMergeResult(int movedCount, int deletedCount) {
    }
}
