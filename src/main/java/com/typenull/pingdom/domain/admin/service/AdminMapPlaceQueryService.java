package com.typenull.pingdom.domain.admin.service;

import com.typenull.pingdom.domain.admin.dto.place.AdminMapPlaceDetailResponse;
import com.typenull.pingdom.domain.admin.dto.place.AdminMapPlaceResponse;

public interface AdminMapPlaceQueryService {

    AdminMapPlaceResponse listPlaces(int page, int limit);

    AdminMapPlaceDetailResponse getPlace(Long placeId);
}
