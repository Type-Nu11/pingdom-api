package com.typenull.pingdom.place.application.service;

import com.typenull.pingdom.place.api.dto.place.PlaceDetailResponse;
import com.typenull.pingdom.place.api.dto.place.PlaceListItem;
import com.typenull.pingdom.place.api.dto.place.PlaceListResponse;
import com.typenull.pingdom.place.domain.MapPlace;
import com.typenull.pingdom.place.infrastructure.persistence.MapPlaceRepository;
import com.typenull.pingdom.shared.exception.MapErrorCode;
import com.typenull.pingdom.shared.exception.MapException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlaceQueryServiceImpl implements PlaceQueryService {

    private final MapPlaceRepository mapPlaceRepository;

    @Override
    @Transactional(readOnly = true)
    public PlaceListResponse listPlaces(int page, int limit, String keyword) {
        int safePage = Math.max(page, 1);
        int safeLimit = Math.max(1, Math.min(limit, 100));
        Pageable pageable = PageRequest.of(safePage - 1, safeLimit, Sort.by(Sort.Direction.DESC, "id"));

        Page<MapPlace> placePage;
        if (keyword == null || keyword.isBlank()) {
            placePage = mapPlaceRepository.findAll(pageable);
        } else {
            placePage = mapPlaceRepository.findByNameContaining(keyword, pageable);
        }

        List<PlaceListItem> places = placePage.getContent()
                .stream()
                .map(this::toListItem)
                .toList();

        return PlaceListResponse.of(
                places,
                safePage,
                safeLimit,
                placePage.getTotalElements(),
                placePage.getTotalPages()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PlaceDetailResponse getPlace(Long placeId) {
        MapPlace mapPlace = mapPlaceRepository.findById(placeId)
                .orElseThrow(() -> new MapException(MapErrorCode.PLACE_NOT_FOUND));

        return new PlaceDetailResponse(
                mapPlace.getId(),
                mapPlace.getName(),
                mapPlace.getAddress(),
                mapPlace.getLatitude(),
                mapPlace.getLongitude(),
                mapPlace.getRegistrant()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public PlaceListResponse listBookmarkedPlaces(Long userId, int page, int limit) {
        int safePage = Math.max(page, 1);
        int safeLimit = Math.max(1, Math.min(limit, 100));
        Pageable pageable = PageRequest.of(safePage - 1, safeLimit);

        Page<MapPlace> placePage = mapPlaceRepository.findBookmarkedPlacesByUserId(userId, pageable);
        List<PlaceListItem> places = placePage.getContent().stream()
                .map(this::toListItem)
                .toList();

        return PlaceListResponse.of(
                places,
                safePage,
                safeLimit,
                placePage.getTotalElements(),
                placePage.getTotalPages()
        );
    }

    private PlaceListItem toListItem(MapPlace mapPlace) {
        return new PlaceListItem(
                mapPlace.getId(),
                mapPlace.getName(),
                mapPlace.getAddress(),
                mapPlace.getLatitude(),
                mapPlace.getLongitude()
        );
    }

}
