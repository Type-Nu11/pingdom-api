package com.typenull.pingdom.place.application.service.place;

import com.typenull.pingdom.place.api.dto.bookmark.BookmarkCreateRequest;
import com.typenull.pingdom.place.api.dto.bookmark.BookmarkCreateResponse;
import com.typenull.pingdom.place.api.dto.bookmark.BookmarkRemoveResponse;
import com.typenull.pingdom.place.application.service.recommendation.feedback.PlaceRecommendationConversionService;
import com.typenull.pingdom.place.application.service.recommendation.snapshot.PlaceRecommendationSnapshotService;
import com.typenull.pingdom.place.domain.place.core.MapBookmark;
import com.typenull.pingdom.place.domain.place.core.MapBookmarkTrendEvent;
import com.typenull.pingdom.place.domain.place.discovery.PlaceDiscoveryStatus;
import com.typenull.pingdom.place.domain.place.operating.PlaceOperatingStatus;
import com.typenull.pingdom.place.domain.recommendation.engagement.PlaceRecommendationConversionType;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapBookmarkRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapBookmarkTrendEventRepository;
import com.typenull.pingdom.place.infrastructure.persistence.place.MapPlaceRepository;
import com.typenull.pingdom.place.infrastructure.support.MapMessages;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MapBookmarkService {

    private final MapBookmarkRepository mapBookmarkRepository;
    private final MapBookmarkTrendEventRepository mapBookmarkTrendEventRepository;
    private final MapPlaceRepository mapPlaceRepository;
    private final PlaceRecommendationSnapshotService placeRecommendationSnapshotService;
    private final PlaceRecommendationConversionService placeRecommendationConversionService;
    private final Clock clock;

    @Transactional
    public BookmarkCreateResponse createBookmark(BookmarkCreateRequest request, long userId) {
        Long placeId = request.placeId();

        boolean placeExists = mapPlaceRepository.existsByIdAndOperatingStatusAndDiscoveryStatus(
                placeId,
                PlaceOperatingStatus.OPERATING,
                PlaceDiscoveryStatus.VISIBLE
        );
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

        MapBookmark saved;
        try {
            saved = mapBookmarkRepository.saveAndFlush(bookmark);
        } catch (DataIntegrityViolationException exception) {
            throw new MapException(MapErrorCode.BOOKMARK_ALREADY_EXISTS);
        }
        mapBookmarkTrendEventRepository.save(MapBookmarkTrendEvent.added(userId, placeId, LocalDateTime.now(clock)));
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
        if (mapBookmarkRepository.deleteByPlaceIdAndUserId(placeId, userId) == 0) {
            throw new MapException(MapErrorCode.BOOKMARK_NOT_FOUND);
        }
        mapBookmarkTrendEventRepository.save(MapBookmarkTrendEvent.removed(userId, placeId, LocalDateTime.now(clock)));
        placeRecommendationSnapshotService.refresh(placeId);

        return new BookmarkRemoveResponse(userId, placeId, MapMessages.BOOKMARK_REMOVED);
    }
}
