package com.typenull.pingdom.place.application.service.place;

import com.typenull.pingdom.place.api.dto.place.detail.PlaceDetailResponse;
import com.typenull.pingdom.place.api.dto.place.autocomplete.PlaceAutocompleteResponse;
import com.typenull.pingdom.place.api.dto.place.list.PlaceListResponse;

public interface PlaceQueryService {
    PlaceListResponse listPlaces(PlaceSearchCondition condition);

    PlaceAutocompleteResponse autocompletePlaces(String keyword, int limit, Double latitude, Double longitude);

    PlaceDetailResponse getPlace(Long placeId);

    PlaceListResponse listBookmarkedPlaces(Long userId, int page, int limit);
}
