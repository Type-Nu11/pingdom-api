package com.typenull.pingdom.place.application.service;

import com.typenull.pingdom.place.api.dto.PlaceDetailResponse;
import com.typenull.pingdom.place.api.dto.PlaceListResponse;

public interface PlaceQueryService {
    PlaceListResponse listPlaces(int page, int limit, String keyword);

    PlaceDetailResponse getPlace(Long placeId);
}
