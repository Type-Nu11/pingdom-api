package com.typenull.pingdom.place.application.service.place;

import com.typenull.pingdom.place.api.dto.place.PlaceDetailResponse;
import com.typenull.pingdom.place.api.dto.place.PlaceAutocompleteResponse;
import com.typenull.pingdom.place.api.dto.place.PlaceListResponse;

public interface PlaceQueryService {
    PlaceListResponse listPlaces(int page, int limit, String keyword);

    PlaceAutocompleteResponse autocompletePlaces(String keyword, int limit, Double latitude, Double longitude);

    PlaceDetailResponse getPlace(Long placeId);

    PlaceListResponse listBookmarkedPlaces(Long userId, int page, int limit);
}
