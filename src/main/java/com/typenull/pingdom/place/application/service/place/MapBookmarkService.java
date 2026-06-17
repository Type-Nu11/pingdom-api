package com.typenull.pingdom.place.application.service.place;

import com.typenull.pingdom.place.api.dto.bookmark.BookmarkCreateRequest;
import com.typenull.pingdom.place.api.dto.bookmark.BookmarkCreateResponse;
import com.typenull.pingdom.place.api.dto.bookmark.BookmarkRemoveResponse;
import com.typenull.pingdom.place.application.service.recommendation.PlaceRecommendationConversionService;
import com.typenull.pingdom.place.application.service.recommendation.PlaceRecommendationSnapshotService;
import com.typenull.pingdom.place.domain.place.MapBookmark;
import com.typenull.pingdom.place.domain.recommendation.PlaceRecommendationConversionType;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapBookmarkRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.support.MapMessages;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MapBookmarkService {

    private final MapBookmarkRepository mapBookmarkRepository;
    private final MapPlaceRepository mapPlaceRepository;
    private final PlaceRecommendationSnapshotService placeRecommendationSnapshotService;
    private final PlaceRecommendationConversionService placeRecommendationConversionService;

    @Transactional
    public BookmarkCreateResponse createBookmark(BookmarkCreateRequest request, long userId) {
        Long placeId = request.placeId();

        boolean placeExists = mapPlaceRepository.existsById(placeId);
        if (!placeExists) {
            throw new MapException(MapErrorCode.PLACE_NOT_FOUND);
        }

        boolean alreadyExists = mapBookmarkRepository.existsByUserIdAndPlaceId(userId, placeId);
        if (alreadyExists) {
            throw new MapException(MapErrorCode.BOOKMARK_ALREADY_EXISTS);
        }

        MapBookmark bookmark = MapBookmark.builder()
                .userId(userId)
                .placeId(placeId)
                .build();

        MapBookmark saved = mapBookmarkRepository.save(bookmark);
        placeRecommendationSnapshotService.refresh(placeId);
        placeRecommendationConversionService.recordConversionIfEligible(
                userId,
                placeId,
                PlaceRecommendationConversionType.BOOKMARK
        );
        return new BookmarkCreateResponse(saved.getId(), saved.getPlaceId(), MapMessages.BOOKMARK_CREATED);
    }

    @Transactional
    public BookmarkRemoveResponse removeBookmark(Long placeId, long userId) {
        if(!mapBookmarkRepository.existsByUserIdAndPlaceId(userId, placeId)){
            throw new MapException(MapErrorCode.BOOKMARK_NOT_FOUND);
        }

        mapBookmarkRepository.deleteByPlaceIdAndUserId(placeId, userId);
        placeRecommendationSnapshotService.refresh(placeId);

        return new BookmarkRemoveResponse(userId, placeId, MapMessages.BOOKMARK_REMOVED);
    }
}
