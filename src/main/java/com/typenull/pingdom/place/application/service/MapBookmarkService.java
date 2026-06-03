package com.typenull.pingdom.place.application.service;

import com.typenull.pingdom.place.support.MapMessages;
import com.typenull.pingdom.place.domain.MapBookmark;
import com.typenull.pingdom.place.api.dto.BookmarkCreateRequest;
import com.typenull.pingdom.place.api.dto.BookmarkCreateResponse;
import com.typenull.pingdom.domain.map.exception.MapErrorCode;
import com.typenull.pingdom.domain.map.exception.MapException;
import com.typenull.pingdom.place.domain.repository.MapBookmarkRepository;
import com.typenull.pingdom.place.domain.repository.MapPlaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MapBookmarkService {

    private final MapBookmarkRepository mapBookmarkRepository;
    private final MapPlaceRepository mapPlaceRepository;

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
        return new BookmarkCreateResponse(saved.getId(), saved.getPlaceId(), MapMessages.BOOKMARK_CREATED);
    }
}
