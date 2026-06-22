package com.typenull.pingdom.place.application.service.place;

import com.typenull.pingdom.place.api.dto.place.PlaceDetailResponse;
import com.typenull.pingdom.place.api.dto.place.PlaceListResponse;

public interface PlaceQueryService {
    PlaceListResponse listPlaces(PlaceSearchCondition condition);

    PlaceDetailResponse getPlace(Long placeId);

    PlaceListResponse listBookmarkedPlaces(Long userId, int page, int limit);
}
